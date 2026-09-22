package com.xperience.hero.gate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Every request passes here. Nothing under {@code /api} is reachable without a link except the open path, which
 * permits only event creation (U1) and never resolves or reveals an existing event.
 */
@Component
@RequiredArgsConstructor
public class GateInterceptor implements HandlerInterceptor {

	public static final String HOST_ATTRIBUTE = "hero.hostAccess";
	public static final String GUEST_ATTRIBUTE = "hero.guestAccess";

	private static final String BEARER = "Bearer ";

	private final AccessGate gate;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String path = request.getRequestURI();
		if (isOpenPath(request.getMethod(), path)) {
			return true;
		}
		if (path.startsWith("/api/host/")) {
			request.setAttribute(HOST_ATTRIBUTE, gate.resolveHost(tokenOf(request)));
			return true;
		}
		if (path.equals("/api/guest") || path.startsWith("/api/guest/")) {
			request.setAttribute(GUEST_ATTRIBUTE, gate.resolveGuest(tokenOf(request)));
			return true;
		}
		// Deny by default: a new endpoint is unreachable until it is placed on one of the paths above.
		throw new LinkInvalidException();
	}

	private static boolean isOpenPath(String method, String path) {
		return "POST".equals(method) && "/api/events".equals(path);
	}

	private static String tokenOf(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		return header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
	}
}
