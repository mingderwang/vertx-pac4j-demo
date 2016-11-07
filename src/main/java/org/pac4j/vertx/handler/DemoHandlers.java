/*
  Copyright 2014 - 2015 pac4j organization

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.vertx.handler;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.HandlebarsTemplateEngine;
import org.pac4j.core.config.Config;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.jwt.profile.JwtGenerator;
import org.pac4j.vertx.VertxProfileManager;
import org.pac4j.vertx.VertxWebContext;
import org.pac4j.vertx.auth.Pac4jAuthProvider;
import org.pac4j.vertx.handler.impl.ApplicationLogoutHandler;
import org.pac4j.vertx.handler.impl.ApplicationLogoutHandlerOptions;
import org.pac4j.vertx.handler.impl.SecurityHandler;
import org.pac4j.vertx.handler.impl.SecurityHandlerOptions;

import java.util.List;
import java.util.function.BiConsumer;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 * A collection of basic handlers printing dynamic html for the demo application.
 * 
 * @author Michael Remond/Jeremy Prime
 * @since 1.0.0
 *
 */
public class DemoHandlers {

    public static Handler<RoutingContext> indexHandler() {
        final HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create();
        return rc -> {
// we define a hardcoded title for our application
            rc.put("name", "Vert.x Web");
            final List<CommonProfile> profile = getUserProfiles(rc);
            rc.put("userProfiles", profile);

            // and now delegate to the engine to render it.
            engine.render(rc, "templates/index.hbs", res -> {
                if (res.succeeded()) {
                    rc.response().end(res.result());
                } else {
                    rc.fail(res.cause());
                }
            });
        };
    }

    public static Handler<RoutingContext> setContentTypeHandler(final CharSequence contentType) {
        return rc -> {
            rc.response().putHeader(CONTENT_TYPE, contentType);
            rc.next();
        };
    }

    public static Handler<RoutingContext> authHandler(final Vertx vertx,
                                                      final Config config,
                                                      final Pac4jAuthProvider provider,
                                                      final SecurityHandlerOptions options) {
        return new SecurityHandler(vertx, config, provider, options);
    }

    public static Handler<RoutingContext> logoutHandler(final Vertx vertx, final Config config) {
        return new ApplicationLogoutHandler(vertx, new ApplicationLogoutHandlerOptions(), config);
    }

    public static Handler<RoutingContext> protectedIndexHandler() {
        return generateProtectedIndex((rc, buf) -> rc.response().end(buf));
    }

    public static Handler<RoutingContext> loginFormHandler(final Config config) {
        final HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create();
        final FormClient formClient = (FormClient) config.getClients().findClient("FormClient");
        final String url = formClient.getCallbackUrl();

        return rc -> {
            rc.put("url", url);
            engine.render(rc, "templates/loginForm.hbs", res -> {
                if (res.succeeded()) {
                    rc.response().end(res.result());
                } else {
                    rc.fail(res.cause());
                }
            });
        };
    }

    public static Handler<RoutingContext> formIndexJsonHandler() {

        return generateProtectedIndex((rc, buf) -> {
            final JsonObject json = new JsonObject()
                    .put("content", buf.toString());
            rc.response().end(json.encodePrettily());
        });

    }

    public static Handler<RoutingContext> jwtGenerator(final JsonObject jsonConf) {

        final HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create();

        return rc -> {
            final List<CommonProfile> profiles = getUserProfiles(rc);
            final JwtGenerator generator = new JwtGenerator(jsonConf.getString("jwtSalt"));
            String token = "";
            if (CommonHelper.isNotEmpty(profiles)) {
                token = generator.generate(profiles.get(0));
            }
            rc.put("token", token);
            engine.render(rc, "templates/jwt.hbs", res -> {
                if (res.succeeded()) {
                    rc.response().end(res.result());
                } else {
                    rc.fail(res.cause());
                }
            });
        };
    }

    public static Handler<RoutingContext> generateProtectedIndex(final BiConsumer<RoutingContext, Buffer> generatedContentConsumer) {
        final HandlebarsTemplateEngine engine = HandlebarsTemplateEngine.create();

        return rc -> {
            // and now delegate to the engine to render it.

            final List<CommonProfile> profile = getUserProfiles(rc);
            rc.put("userProfiles", profile);

            engine.render(rc, "templates/protectedIndex.hbs", res -> {
                if (res.succeeded()) {
                    generatedContentConsumer.accept(rc, res.result());
                } else {
                    rc.fail(res.cause());
                }
            });
        };
    }

    private static List<CommonProfile> getUserProfiles(final RoutingContext rc) {
        final ProfileManager<CommonProfile> profileManager = new VertxProfileManager(new VertxWebContext(rc));
        return profileManager.getAll(true);
    }
}
