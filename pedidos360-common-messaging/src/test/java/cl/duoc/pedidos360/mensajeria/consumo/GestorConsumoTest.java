package cl.duoc.pedidos360.mensajeria.consumo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.duoc.pedidos360.mensajeria.config.MensajeriaProperties;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Politica de confirmacion y de errores de los consumidores: confirmar, reintentar o mandar
 * a la DLQ. Es el nucleo de la mensajeria, asi que se prueba caso por caso.
 */
class GestorConsumoTest {

  private static final long ETIQUETA = 7L;
  private static final String COLA = "pedidos360.notificaciones";

  private RabbitTemplate rabbitTemplate;
  private Channel canal;
  private MensajeriaProperties propiedades;
  private GestorConsumo gestor;

  @BeforeEach
  void prepararDependencias() {
    rabbitTemplate = mock(RabbitTemplate.class);
    canal = mock(Channel.class);
    propiedades = new MensajeriaProperties();
    gestor = new GestorConsumo(rabbitTemplate, propiedades);
  }

  private Message mensaje(Integer intentosPrevios) {
    MessageProperties propiedadesMensaje = new MessageProperties();
    propiedadesMensaje.setDeliveryTag(ETIQUETA);
    propiedadesMensaje.setConsumerQueue(COLA);
    if (intentosPrevios != null) {
      propiedadesMensaje.setHeader(GestorConsumo.CABECERA_INTENTOS, intentosPrevios);
    }
    return new Message("{}".getBytes(StandardCharsets.UTF_8), propiedadesMensaje);
  }

  @Test
  @DisplayName("Procesar sin errores confirma el mensaje con ACK")
  void exitoConfirmaMensaje() throws IOException {
    gestor.procesar("evento", mensaje(null), canal, "PED-1", evento -> {});

    verify(canal).basicAck(ETIQUETA, false);
    verify(canal, never()).basicNack(ETIQUETA, false, false);
  }

  @Test
  @DisplayName("Un error no recuperable va directo a la DLQ, sin reintentos")
  void errorNoRecuperableVaALaDlq() throws IOException {
    gestor.procesar(
        "evento",
        mensaje(null),
        canal,
        "PED-1",
        evento -> {
          throw new EventoNoProcesableException("el evento llego sin numero de pedido");
        });

    verify(canal).basicNack(ETIQUETA, false, false);
    verify(canal, never()).basicAck(ETIQUETA, false);
    verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
  }

  @Test
  @DisplayName("Un error recuperable reencola el mensaje en la cola de espera")
  void errorRecuperableProgramaReintento() throws IOException {
    gestor.procesar(
        "evento",
        mensaje(null),
        canal,
        "PED-1",
        evento -> {
          throw new IllegalStateException("la base de datos no responde");
        });

    verify(rabbitTemplate).send(eq(""), eq(COLA + ".retry"), any(Message.class));
    verify(canal).basicAck(ETIQUETA, false);
    verify(canal, never()).basicNack(ETIQUETA, false, false);
  }

  @Test
  @DisplayName("Al agotar los intentos configurados el mensaje se descarta a la DLQ")
  void alAgotarIntentosDescarta() throws IOException {
    Message mensaje = mensaje(propiedades.getMaxIntentos() - 1);

    gestor.procesar(
        "evento",
        mensaje,
        canal,
        "PED-1",
        evento -> {
          throw new IllegalStateException("la base de datos sigue sin responder");
        });

    verify(canal).basicNack(ETIQUETA, false, false);
    verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    Object motivo = mensaje.getMessageProperties().getHeader(GestorConsumo.CABECERA_MOTIVO);
    assertThat(motivo.toString()).contains("IllegalStateException");
  }

  @Test
  @DisplayName("Si el reintento no se puede publicar, el mensaje se descarta a la DLQ")
  void siFallaElReintentoDescarta() throws IOException {
    doThrow(new AmqpException("broker caido"))
        .when(rabbitTemplate)
        .send(eq(""), eq(COLA + ".retry"), any(Message.class));

    gestor.procesar(
        "evento",
        mensaje(null),
        canal,
        "PED-1",
        evento -> {
          throw new IllegalStateException("fallo temporal");
        });

    verify(canal).basicNack(ETIQUETA, false, false);
    verify(canal, never()).basicAck(ETIQUETA, false);
  }

  @Test
  @DisplayName("El contador de intentos se guarda en la cabecera del mensaje reintentado")
  void guardaElContadorDeIntentos() throws IOException {
    Message mensaje = mensaje(1);

    gestor.procesar(
        "evento",
        mensaje,
        canal,
        "PED-1",
        evento -> {
          throw new IllegalStateException("fallo temporal");
        });

    Object intentos = mensaje.getMessageProperties().getHeader(GestorConsumo.CABECERA_INTENTOS);
    assertThat(intentos).isEqualTo(2);
  }
}
