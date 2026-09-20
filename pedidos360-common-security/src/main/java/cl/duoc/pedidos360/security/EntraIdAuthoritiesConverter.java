package cl.duoc.pedidos360.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Traduce los claims del IDaaS a autoridades de Spring Security:
 *
 * <ul>
 *   <li>{@code roles} -> {@code ROLE_<ROL>} para usar con {@code hasRole(..)}
 *   <li>{@code scp} -> {@code SCOPE_<scope>} para usar con {@code hasAuthority("SCOPE_..")}
 * </ul>
 */
public class EntraIdAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = new ArrayList<>();
    for (String role : EntraIdClaims.roles(jwt)) {
      if (role != null && !role.isBlank()) {
        authorities.add(
            new SimpleGrantedAuthority(
                "ROLE_" + role.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_')));
      }
    }
    for (String scope : EntraIdClaims.scopes(jwt)) {
      authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
    }
    return authorities;
  }
}
