import browser.LoginBrowser;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.teamdev.jxbrowser.navigation.LoadUrlParams;
import config.AppContext;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.reactive.function.client.WebClient;
import security.AuthenticationProvider;
import security.DesktopOAuth2LoginAuthenticationToken;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;


public class Main{

    ConfigurableApplicationContext context;

    public static void main(String[] args) throws InterruptedException {
        Main application = new Main(args);
        application.login();
        String message = application.callHello();
        application.displayMessage(message);
    }

    public Main(String[] args) {
        context = new SpringApplicationBuilder(AppContext.class)
                .bannerMode(Banner.Mode.OFF).headless(false).web(WebApplicationType.NONE)
                .run(args);
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_GLOBAL);
    }

    private String callHello() {
        WebClient webClient = context.getBean(WebClient.class);

        return webClient.get().uri("hello").retrieve().bodyToMono(String.class).block();
    }

    private void login() throws InterruptedException {
        AtomicBoolean closed = new AtomicBoolean(false);

        JFrame frame = new JFrame("Login Frame");

        Environment env = context.getBean(Environment.class);

        System.setProperty("jxbrowser.license.key", env.getProperty("example.jxbrowser"));

        String issuerUrl = env.getProperty("example.keycloak.issuerUrl");
        String realm = env.getProperty("example.keycloak.realm");
        String client = env.getProperty("example.keycloak.client");

        // Generate random state string to securely pair the callback to this request
        State state = new State();

        String redirectURI = "https://www.acuity-solutions.fr";

        LoginBrowser browser = new LoginBrowser(issuerUrl, client, state,
                (tokenEndpointResponse) -> {
                    handleAuthentication(tokenEndpointResponse);

                    SwingUtilities.invokeLater(() -> {
                        frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                    });
                });

        // Closing the engine when app frame is about to close
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                synchronized (closed) {
                    closed.set(true);
                    closed.notifyAll();
                }
            }
        });

        ClientID clientID = new ClientID(client);

        // The client callback URL
        URI callback = null;
        try {
            callback = new URI(redirectURI);


        // Generate nonce for the ID token
        Nonce nonce = new Nonce();

        // Compose the OpenID authentication request (for the code flow)
        AuthenticationRequest request = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope("openid"),
                clientID,
                callback)
                .endpointURI(new URI(issuerUrl + "/auth/realms/"+ realm + "/protocol/openid-connect/auth"))
                .state(state)
                .nonce(nonce)
                .build();



        browser.loadUrl(request.toURI().toString());

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        // Loading the required web page
//        browser.loadUrl(env.getProperty("example.loginPage"));

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(browser.getView(), BorderLayout.CENTER);
        frame.setSize(800, 600);
        frame.setVisible(true);

        synchronized(closed) {
            while (!closed.get()) {
                closed.wait();
            }
        }

    }



    private static void displayMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("OIDC Demo");
            frame.setMinimumSize(new Dimension(800, 400));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JLabel myLabel = new JLabel(message, SwingConstants.CENTER);
            myLabel.setFont(new Font("Serif", Font.BOLD, 22));
            myLabel.setBackground(Color.gray);
            myLabel.setOpaque(true);
            myLabel.setPreferredSize(new Dimension(100, 80));

            frame.getContentPane().add(myLabel, BorderLayout.NORTH);
            frame.setVisible(true);
        });
    }

    private void handleAuthentication(OIDCTokenResponse theTokenEndpointResponse) {

        // 1. This assumes that SecurityContext is already configured for GLOBAL mode
        var aAuthenticationProvider = context.getBean(AuthenticationProvider.class);
        var aAuthentication = aAuthenticationProvider.registerAuthentication(theTokenEndpointResponse);

        DesktopOAuth2LoginAuthenticationToken aDesktopAuthentication = (DesktopOAuth2LoginAuthenticationToken) aAuthentication;
        OidcUser principal = aDesktopAuthentication.getPrincipal();

        System.out.println(principal.getName());
    }

}