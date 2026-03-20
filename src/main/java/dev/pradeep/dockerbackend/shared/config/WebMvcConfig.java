package dev.pradeep.dockerbackend.shared.config;

import dev.pradeep.dockerbackend.resource.interceptor.PermissionInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply to all /api/** paths. Public endpoints are unaffected because
        // @RequiresPermission is not present on them, so the interceptor passes through.
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**");
    }
}
