package cl.duoc.pedidos360.security;

/** Roles de aplicacion definidos en el registro de la API en Microsoft Entra ID. */
public final class SecurityRoles {

  /** Administra catalogo, clientes y puede anular pedidos. */
  public static final String ADMIN = "Pedidos.Admin";

  /** Crea y gestiona pedidos de cualquier cliente. */
  public static final String VENDEDOR = "Pedidos.Vendedor";

  /** Solo lectura sobre pedidos y catalogo. */
  public static final String CLIENTE = "Pedidos.Cliente";

  /** Expresiones listas para usar en {@code @PreAuthorize}. */
  public static final String HAS_ADMIN = "hasRole('PEDIDOS_ADMIN')";

  public static final String HAS_VENDEDOR = "hasRole('PEDIDOS_VENDEDOR')";

  public static final String HAS_CLIENTE = "hasRole('PEDIDOS_CLIENTE')";

  public static final String HAS_ANY = "hasAnyRole('PEDIDOS_ADMIN','PEDIDOS_VENDEDOR','PEDIDOS_CLIENTE')";

  public static final String HAS_GESTION = "hasAnyRole('PEDIDOS_ADMIN','PEDIDOS_VENDEDOR')";

  private SecurityRoles() {}
}
