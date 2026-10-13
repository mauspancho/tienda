import com.tienda.pos.TiendaPosApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the actual Springdoc contract from inspected backend classes with an isolated H2 DB. */
public class GenerateBackendOpenApi {
    public static void main(String[] args) throws Exception {
        System.setProperty("tienda.setup-mode", "false");
        try (var context = SpringApplication.run(TiendaPosApplication.class,
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:android-contract;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--spring.flyway.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=create-drop", "--springdoc.api-docs.enabled=true")) {
            // Documentation now requires web authentication. Supply a test principal only inside this isolated context.
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
            var response = mvc.perform(get("/api-docs").with(user("contract-probe").roles("ADMIN"))).andReturn().getResponse();
            if (response.getStatus() != 200 || !response.getContentAsString().contains("/api/v1/credits/summary")) throw new IllegalStateException("Updated credit contract unavailable");
            Files.writeString(Path.of(args[0]), response.getContentAsString());
            System.out.println("Updated backend OpenAPI generated from running Springdoc");
        }
    }
}
