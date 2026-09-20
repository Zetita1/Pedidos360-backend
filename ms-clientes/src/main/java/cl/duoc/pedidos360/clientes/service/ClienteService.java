package cl.duoc.pedidos360.clientes.service;

import cl.duoc.pedidos360.clientes.domain.Cliente;
import cl.duoc.pedidos360.clientes.repository.ClienteRepository;
import cl.duoc.pedidos360.clientes.web.ClienteDtos.ActualizarClienteRequest;
import cl.duoc.pedidos360.clientes.web.ClienteDtos.CrearClienteRequest;
import cl.duoc.pedidos360.web.RecursoNoEncontradoException;
import cl.duoc.pedidos360.web.ReglaNegocioException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reglas de negocio de la ficha de clientes. */
@Service
public class ClienteService {

  private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

  private final ClienteRepository repository;

  public ClienteService(ClienteRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Cliente> buscar(String texto, boolean soloActivos) {
    String normalizado = (texto == null || texto.isBlank()) ? null : texto.trim();
    return repository.buscar(normalizado, soloActivos);
  }

  @Transactional(readOnly = true)
  public Cliente obtener(Long id) {
    return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("cliente", id));
  }

  @Transactional(readOnly = true)
  public Cliente obtenerPorRut(String rut) {
    return repository
        .findByRut(normalizarRut(rut))
        .orElseThrow(() -> new RecursoNoEncontradoException("No existe cliente con rut " + rut));
  }

  /**
   * Devuelve la ficha del cliente asociada al usuario autenticado en el IDaaS.
   *
   * @param usuarioOid claim {@code oid} del token
   * @return cliente vinculado
   */
  @Transactional(readOnly = true)
  public Cliente obtenerPorUsuario(String usuarioOid) {
    return repository
        .findByUsuarioOid(usuarioOid)
        .orElseThrow(
            () ->
                new RecursoNoEncontradoException(
                    "El usuario autenticado no tiene una ficha de cliente asociada"));
  }

  @Transactional
  public Cliente crear(CrearClienteRequest request, String usuarioOid) {
    String rut = normalizarRut(request.rut());
    if (repository.existsByRut(rut)) {
      throw new ReglaNegocioException("Ya existe un cliente con el rut " + rut);
    }
    Cliente cliente =
        new Cliente(
            rut,
            request.razonSocial().trim(),
            request.email().trim().toLowerCase(Locale.ROOT),
            request.telefono(),
            request.segmento(),
            request.direccion().aDominio(),
            usuarioOid);
    Cliente guardado = repository.save(cliente);
    log.info("Cliente creado id={} rut={}", guardado.getId(), guardado.getRut());
    return guardado;
  }

  @Transactional
  public Cliente actualizar(Long id, ActualizarClienteRequest request) {
    Cliente cliente = obtener(id);
    cliente.actualizarDatos(
        request.razonSocial().trim(),
        request.email().trim().toLowerCase(Locale.ROOT),
        request.telefono(),
        request.segmento(),
        request.direccion().aDominio());
    return cliente;
  }

  @Transactional
  public Cliente vincularUsuario(Long id, String usuarioOid) {
    Cliente cliente = obtener(id);
    cliente.vincularUsuario(usuarioOid);
    return cliente;
  }

  @Transactional
  public void desactivar(Long id) {
    obtener(id).desactivar();
    log.info("Cliente id={} desactivado", id);
  }

  private String normalizarRut(String rut) {
    return rut == null ? null : rut.trim().toUpperCase(Locale.ROOT).replace(".", "");
  }
}
