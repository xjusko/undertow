package io.undertow.server.security;

import io.undertow.predicate.Predicates;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.session.InMemorySessionManager;
import io.undertow.server.session.SessionAttachmentHandler;
import io.undertow.server.session.SessionCookieConfig;
import io.undertow.testutils.DefaultServer;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.undertow.server.security.FormAuthTestCase._testFormAuth;

@RunWith(DefaultServer.class)
public class SpnegoFormAuthenticationTestCase extends SpnegoAuthenticationTestCase{

    protected boolean cachingRequired() {
        return true;
    }

    @Override
    protected void setRootHandler(HttpHandler current) {
        final PredicateHandler handler = new PredicateHandler(Predicates.path("/login"), new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                exchange.getResponseSender().send("Login Page");
            }
        }, current);
        super.setRootHandler(new SessionAttachmentHandler(handler, new InMemorySessionManager("test"), new SessionCookieConfig()));
    }

    @Override
    protected List<AuthenticationMechanism> getTestMechanisms() {
        ArrayList<AuthenticationMechanism> mechanisms = new ArrayList<>(super.getTestMechanisms());
        mechanisms.addAll(FormAuthTestCase.getTestMechanism());

        return mechanisms;
    }

    @Test
    public void testSuccess() throws IOException {
        _testFormAuth();
    }
}
