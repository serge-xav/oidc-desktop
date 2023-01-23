package browser;

import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.*;
import com.teamdev.jxbrowser.browser.Browser;
import com.teamdev.jxbrowser.browser.event.ConsoleMessageReceived;
import com.teamdev.jxbrowser.engine.Engine;
import com.teamdev.jxbrowser.engine.EngineOptions;
import com.teamdev.jxbrowser.js.ConsoleMessage;

import com.teamdev.jxbrowser.navigation.Navigation;
import com.teamdev.jxbrowser.navigation.event.NavigationRedirected;
import com.teamdev.jxbrowser.view.swing.BrowserView;



import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

import static com.teamdev.jxbrowser.engine.RenderingMode.HARDWARE_ACCELERATED;

public class LoginBrowser {

    private String issuerUrl;
    private String client;

    State state;

    private String redirectURI = "https://www.acuity-solutions.fr";

    Consumer<OIDCTokenResponse> authenticationHandler;




    private Browser browser;

    public LoginBrowser (String issuerUrl, String client, State state, Consumer<OIDCTokenResponse> authenticationHandler) {
        this.issuerUrl = issuerUrl;
        this.client = client;
        this.authenticationHandler = authenticationHandler;
        this.state = state;
        initBrowser();
    }

    private void initBrowser() {
        // Creating and running Chromium engine
        Engine engine = Engine.newInstance(
                EngineOptions.newBuilder(HARDWARE_ACCELERATED).build());

        browser = engine.newBrowser();



        Navigation navigation = browser.navigation();

        navigation.on(NavigationRedirected.class, event -> {
                          // The navigation redirect URL.
                          String url2 = event.destinationUrl();
                          URI uri = null;
                          try {
                              uri = new URI(url2);
                          } catch (URISyntaxException e) {
                              throw new RuntimeException(e);
                          }
            if(matchesRedirection(this.redirectURI, url2)) {
                              System.out.println("Redirection occured well");
                              modifyOAuth2ClientSpringSetup(uri);
                              navigation.stop();
                          }
                      });

        browser.on(ConsoleMessageReceived.class, event -> {
            ConsoleMessage consoleMessage = event.consoleMessage();
            System.out.println("Level: " + consoleMessage.level() + ", message: " + consoleMessage.message());
        });

    }

    static boolean matchesRedirection(final String expectedRedirectUriString, final String actualUriString) {
        if(actualUriString == null) {
            return false;
        }

        final URI actualUri = URI.create(actualUriString);
        final URI expectedUri = URI.create(expectedRedirectUriString);

        if(!expectedUri.getScheme().equalsIgnoreCase(actualUri.getScheme())) {
            return false;
        }

        if(!expectedUri.getHost().equalsIgnoreCase(actualUri.getHost())) {
            return false;
        }

        if(expectedUri.getPort() != actualUri.getPort()) {
            return false;
        }

        final String actualPath = actualUri.getPath();
        final String expectedPath = expectedUri.getPath();
        if(actualPath != null) {
            if(expectedPath == null) {
                return false;
            }
            if(!actualPath.startsWith(expectedPath)) {
                return false;
            }
        } else {
            if(expectedPath != null) {
                return false;
            }
        }

        if(actualUri.getScheme().equals("urn") && !actualUriString.startsWith(expectedRedirectUriString)) {
            return false;
        }

        return true;
    }

    void modifyOAuth2ClientSpringSetup(URI url2) {
        try {

            AuthenticationResponse response = AuthenticationResponseParser.parse(url2);

            // Check the state
            if (! response.getState().equals(state)) {
                System.err.println("Unexpected authentication response");
                return;
            }

            if (response instanceof AuthenticationErrorResponse) {
                // The OpenID provider returned an error
                System.err.println(response.toErrorResponse().getErrorObject());
                return;
            }

            // Retrieve the authorisation code, to use it later at the token endpoint
            AuthorizationCode code = response.toSuccessResponse().getAuthorizationCode();

            System.out.println("Authorization code=" + code);

            getTokensviaNimbus(code);

        } catch (ParseException e) {
            e.printStackTrace();
        }
    }


    public BrowserView getView() {
        return BrowserView.newInstance(browser);
    }

    public void loadUrl(String url) {
        browser.navigation().loadUrl(url);
    }

    public void getTokensviaNimbus(AuthorizationCode code){
        // Construct the code grant from the code obtained from the authz endpoint
        // and the original callback URI used at the authz endpoint

        URI callback = null;
        try {
            callback = new URI(this.redirectURI);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        AuthorizationGrant codeGrant = new AuthorizationCodeGrant(code, callback);

        // The credentials to authenticate the client at the token endpoint
        ClientID clientID = new ClientID(client);

        // The token endpoint

        URI tokenEndpoint = null;
        try {
            tokenEndpoint = new URI("http://localhost:8083/auth/realms/pleiade/protocol/openid-connect/token");

        // Make the token request
        TokenRequest request = new TokenRequest(tokenEndpoint, clientID, codeGrant);

        TokenResponse tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

        if (! tokenResponse.indicatesSuccess()) {
            // We got an error response...
            TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
        }

        OIDCTokenResponse successResponse = (OIDCTokenResponse)tokenResponse.toSuccessResponse();


            authenticationHandler.accept(successResponse);

        } catch (URISyntaxException | IOException | ParseException e) {
            throw new RuntimeException(e);
        }


    }
}
