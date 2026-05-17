package top.fengpingtech.solen.app.auth;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Tenant {
    private String id;

    private String appKey;

    private String secretKey;

    private String passKey;

    private String desc;

    private List<String> devicePatterns;

    private List<String> roles;
}
