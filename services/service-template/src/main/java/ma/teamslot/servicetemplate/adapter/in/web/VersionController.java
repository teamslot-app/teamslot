package ma.teamslot.servicetemplate.adapter.in.web;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/template")
public class VersionController {

    private final String version;

    public VersionController(@Value("${app.version:2}") String version) {
        this.version = version;
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("service", "service-template", "version", version);
    }
}
