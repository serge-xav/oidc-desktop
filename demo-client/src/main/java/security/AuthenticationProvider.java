package security;

import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import org.springframework.security.core.Authentication;

public interface AuthenticationProvider {
	Authentication registerAuthentication(OIDCTokenResponse parsedOidcToken);
}
