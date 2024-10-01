package com.neon.tonari.config;

import com.neon.tonari.security.jwt.JwtAuthenticationFilter;
import com.neon.tonari.security.jwt.JwtTokenProvider;
import com.neon.tonari.service.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.client.origin}")
    private String clientOrigin;

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;  // UserDetailsService 주입

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)  // CSRF 설정 비활성화
                .cors(withDefaults())  // CORS 설정 추가
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login**", "/css/**", "/js/**", "/api/auth/token").permitAll()  // 특정 경로 허용
                        .anyRequest().authenticated()  // 그 외의 요청은 인증 필요
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                                response.setStatus(HttpServletResponse.SC_OK);  // OPTIONS 요청에 대해 OK 반환
                            } else {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 상태 코드 반환
                                response.getWriter().write("Unauthorized");
                            }
                        })
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")  // 사용자 정의 로그인 페이지 설정
                        .defaultSuccessUrl("/", true)  // 로그인 성공 후 리디렉션
                        .failureUrl("/login?error=true")
                        .successHandler((request, response, authentication) -> {
                            // JWT 생성
                            String token = jwtTokenProvider.generateToken(authentication.getName());
                            // 프론트엔드로 리디렉션 및 JWT 전달
                            response.sendRedirect(clientOrigin + "/login?token=" + token);
                        })
                );

        http.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService), UsernamePasswordAuthenticationFilter.class);  // 필터 등록

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(clientOrigin)); // 프론트엔드가 배포될 URL로 설정
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization")); // 필요한 헤더 노출 설정
        configuration.setAllowCredentials(true); // Credentials 허용 설정

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}