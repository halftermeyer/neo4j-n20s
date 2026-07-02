package n20s.server;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public class N20sServer {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7474"));
        Javalin app = createApp();
        app.start(port);
    }

    public static Javalin createApp() {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson());
        });
        GraphRoutes.register(app);
        return app;
    }
}
