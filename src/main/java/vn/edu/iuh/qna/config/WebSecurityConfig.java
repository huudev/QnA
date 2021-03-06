package vn.edu.iuh.qna.config;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import vn.edu.iuh.qna.service.impl.UserDetailsServiceImpl;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
	public static final String ROLE_USER = "ROLE_USER";
	public static final String ROLE_ADMIN = "ROLE_ADMIN";
	public static final Map<String, RoleConfig> ROLE_CONFIG = new HashMap<String, RoleConfig>() {
		private static final long serialVersionUID = 1L;
		{
			put(ROLE_USER, RoleConfig.of(ROLE_USER, "/"));
			put(ROLE_ADMIN, RoleConfig.of(ROLE_ADMIN, "/admin"));
		}
	};

	@Autowired
	private UserDetailsServiceImpl userDetailsService;

	@Bean
	public BCryptPasswordEncoder passwordEncoder() {
		BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
		return bCryptPasswordEncoder;
	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {

		http.csrf().disable();

		// Các trang không yêu cầu login
		http.authorizeRequests().antMatchers("/login*", "/logout*").permitAll();

		// Trang /userInfo yêu cầu phải login với vai trò ROLE_USER hoặc ROLE_ADMIN.
		// Nếu chưa login, nó sẽ redirect tới trang /login.
		// http.authorizeRequests().antMatchers("/userInfo").access("hasAnyRole('ROLE_USER',
		// 'ROLE_ADMIN')");
		// Trang chỉ dành cho ADMIN
		// http.authorizeRequests().antMatchers("/admin").access("hasRole('ROLE_ADMIN')");

		// Khi người dùng đã login, với vai trò XX.
		// Nhưng truy cập vào trang yêu cầu vai trò YY,
		// Ngoại lệ AccessDeniedException sẽ ném ra.
		// AuthenticationEntryPoint authenticationEntryPoint = new
		// AuthenticationEntryPoint() {

		// @Override
		// public void commence(HttpServletRequest request, HttpServletResponse
		// response,
		// AuthenticationException authException) throws IOException, ServletException {
		// log.debug("dmmm");
		// }
		// };
		// http.exceptionHandling().authenticationEntryPoint(authenticationEntryPoint);
		http.authorizeRequests().and().exceptionHandling().accessDeniedHandler(authDeniedHandler());

		// Cấu hình cho Login Form.
		http.authorizeRequests().and().formLogin()//
				// Submit URL của trang login
				.loginProcessingUrl("/j_spring_security_check") // Submit URL
				.loginPage("/login")//
				// .defaultSuccessUrl("/userAccountInfo")//
				.successHandler(authSucessHandler()).failureUrl("/login?error=true")//
				.usernameParameter("username")//
				.passwordParameter("password")
				// Cấu hình cho Logout Page.
				.and().logout().logoutUrl("/logout").logoutSuccessUrl("/login");

		// Cấu hình Remember Me.
		http.authorizeRequests().and() //
				.rememberMe().tokenRepository(persistentTokenRepository()) //// 24h
				.tokenValiditySeconds((int) TimeUnit.SECONDS.convert(24, TimeUnit.HOURS));
				//.authenticationSuccessHandler(authSucessHandler()); 

	}

	// Token stored in Memory (Of Web Server).
	@Bean
	public PersistentTokenRepository persistentTokenRepository() {
		InMemoryTokenRepositoryImpl memory = new InMemoryTokenRepositoryImpl();
		return memory;
	}

	@Bean
	public AuthenticationSuccessHandler authSucessHandler() {
		return (request, response, authentication) -> {
			authHandler(request, response);
		};
	}

	@Bean
	public AccessDeniedHandler authDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			authHandler(request, response);
		};
	}

	public void authHandler(HttpServletRequest request, HttpServletResponse response) {
		if (response.isCommitted()) {
			return;
		}
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		try {
			if (auth == null) {
				response.sendRedirect("/login");
			}
			Collection<? extends GrantedAuthority> authors = auth.getAuthorities();
			GrantedAuthority author = authors.iterator().next();
			RoleConfig roleConfig = ROLE_CONFIG.get(author.getAuthority());
			if (roleConfig != null) {
				response.sendRedirect(roleConfig.getPath());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Data
	@AllArgsConstructor(staticName = "of")
	public static class RoleConfig {
		private String role;
		private String path;
	}

}
