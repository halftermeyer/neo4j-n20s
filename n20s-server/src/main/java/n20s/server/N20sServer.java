package n20s.server;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public class N20sServer {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7474"));
        boolean cors = Boolean.parseBoolean(System.getenv().getOrDefault("CORS", "false"));
        Javalin app = createApp(cors);
        app.start(port);
    }

    public static Javalin createApp() {
        return createApp(false);
    }

    public static Javalin createApp(boolean enableCors) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
            if (enableCors) {
                config.bundledPlugins.enableCors(cors -> {
                    cors.addRule(rule -> rule.anyHost());
                });
            }
        });
        GraphRoutes.register(app);
        return app;
    }
}
