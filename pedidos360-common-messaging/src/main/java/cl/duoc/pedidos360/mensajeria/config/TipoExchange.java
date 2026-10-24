package cl.duoc.pedidos360.mensajeria.config;

/**
 * Por que exchange sale un evento.
 *
 * <p>La distincion no es decorativa: un hecho que puede interesarle a varios consumidores
 * viaja por un exchange <b>topic</b>, mientras que una orden con un destinatario exacto va
 * por uno <b>direct</b>, que enruta por coincidencia literal de la routing key.
 */
public enum TipoExchange {

  /** Topic: hechos de dominio que pueden tener varios interesados. */
  EVENTOS,

  /** Direct: ordenes dirigidas a un consumidor concreto. */
  COMANDOS
}
