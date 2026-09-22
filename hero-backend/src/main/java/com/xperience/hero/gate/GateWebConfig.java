package com.xperience.hero.gate;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/** Wires the gate in front of every request, and hands controllers what it resolved. */
@Configuration
@RequiredArgsConstructor
public class GateWebConfig implements WebMvcConfigurer {

	private final GateInterceptor gateInterceptor;

	@Override
	public void addInterceptors(@NonNull InterceptorRegistry registry) {
		registry.addInterceptor(gateInterceptor).addPathPatterns("/api/**");
	}

	@Override
	public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new AccessResolver(HostAccess.class, GateInterceptor.HOST_ATTRIBUTE));
		resolvers.add(new AccessResolver(GuestAccess.class, GateInterceptor.GUEST_ATTRIBUTE));
	}

	private record AccessResolver(Class<?> type, String attribute) implements HandlerMethodArgumentResolver {

		@Override
		public boolean supportsParameter(MethodParameter parameter) {
			return type.equals(parameter.getParameterType());
		}

		@Override
		public Object resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer container,
				NativeWebRequest request, WebDataBinderFactory binderFactory) {
			Object access = request.getAttribute(attribute, RequestAttributes.SCOPE_REQUEST);
			if (access == null) {
				// The gate did not resolve this request; refusing here keeps an unguarded endpoint impossible.
				throw new LinkInvalidException();
			}
			return access;
		}
	}
}
