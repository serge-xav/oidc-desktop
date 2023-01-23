import browser.LoginBrowser;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;


public class Main{
    static JFrame frame = new JFrame("OIDC Demo");
    ConfigurableApplicationContext context;

    public static void main(String[] args) throws InterruptedException {
        Main application = new Main(args);
        application.login();
        for (int i=1; i<15; i++){
            String message = application.callHello();
            application.displayMessage(message, i);
            Thread.sleep(60000);
            frame.setVisible(false);
        }
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

        String issuerUrl = env.getProperty("example.authserver.issuerUrl");
        String client = env.getProperty("example.authserver.client");

        // Generate random state string to securely pair the callback to this request
        State state = new State();

        String redirectURI = "https://www.acuity-solutions.fr";

        // The OpenID provider issuer URL
        Issuer issuer = new Issuer(issuerUrl);

        // Will resolve the OpenID provider metadata automatically
        OIDCProviderMetadata opMetadata = null;
        try {
            opMetadata = OIDCProviderMetadata.resolve(issuer);
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        LoginBrowser browser = new LoginBrowser( opMetadata, client, state,
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
                .endpointURI(opMetadata.getAuthorizationEndpointURI())
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



    private static void displayMessage(String message, int order) {
        SwingUtilities.invokeLater(() -> {

            frame.setMinimumSize(new Dimension(800, 400));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JLabel myLabel = new JLabel(message + " "+ order, SwingConstants.CENTER);
            myLabel.setFont(new Font("Serif", Font.BOLD, 22));
            int red = (int) (Math.random() * 256);
            int green = (int) (Math.random() * 256);
            int blue = (int) (Math.random() * 256);
            myLabel.setBackground(new Color(red, green, blue));
            myLabel.setOpaque(true);
            myLabel.setPreferredSize(new Dimension(100, 80));
            frame.getContentPane().removeAll();
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