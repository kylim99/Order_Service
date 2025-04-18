package com.teamsparta14.order_service.user.jwt;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamsparta14.order_service.global.enums.Role;
import com.teamsparta14.order_service.global.exception.BaseException;
import com.teamsparta14.order_service.global.response.ApiResponse;
import com.teamsparta14.order_service.global.response.ResponseCode;
import com.teamsparta14.order_service.user.service.TokenReissueService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    private final TokenReissueService tokenReissueService;
    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;

    public LoginFilter(AuthenticationManager authenticationManager, JWTUtil jwtUtil, TokenReissueService tokenReissueService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tokenReissueService = tokenReissueService;
        //spring security는 대부분의 로직이 필터 단에서 동작하게 된다. 로그인 또한, 필터에서 처리되고, (자동으로 엔드포인트는 "/login" 이 된다.)
        //UsernamePasswordAuthenticationFilter에서 매핑되어 처리된다. 이 필터를 상속받아 LoginFilter를 만들게 된다.
        //security에서 설정해주는 기본 url("/login")을 /api/auth/login으로 변경
        setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        //클라이언트 요청으로 부터 username, password를 추출한다.
//        String username = obtainUsername(request);
//        String password = obtainPassword(request);
        String username = null;
        String password = null;

        // JSON 형식인지 확인
        if (request.getContentType() != null && request.getContentType().contains("application/json")) {
            try {
                // JSON 데이터를 읽어서 파싱
                ObjectMapper objectMapper = new ObjectMapper();
                Map requestMap = objectMapper.readValue(request.getInputStream(), Map.class);

                username = String.valueOf(requestMap.get("username"));
                password = String.valueOf(requestMap.get("password"));
            } catch (IOException e) {
                throw new AuthenticationServiceException("Failed to parse JSON request", e);
            }
        } else {
            // 기본 form-urlencoded 방식 처리
            username = obtainUsername(request);
            password = obtainPassword(request);
        }

        if (username == null || password == null) {
            throw new AuthenticationServiceException("Username or Password is missing");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(username, password);

        return authenticationManager.authenticate(authToken);

    }

    //로그인 성공시 실행하는 메서드 (여기서 jwt를 발급하면 된다)
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException{
        //유저 이름 찾기
        String username = authentication.getName();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();

        if (!iterator.hasNext()) {
            throw new BaseException("User not found");
        }

        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();

        String access = jwtUtil.createJwt("access",username, role, 600000L*60*24*100); // 24시간 *100 = 100일. 테스트를 위해 기한 늘림
        String refresh = jwtUtil.createJwt("refresh", username, role,86400000L*100); // 24시간 *100 = 100일

        tokenReissueService.RefreshTokenSave(username,refresh,86400000L*100);

        // 로그인 성공
        ResponseEntity<ApiResponse<String>> responseBody = ResponseEntity.ok().body(ApiResponse.success("로그인을 성공하였습니다."));

        response.setHeader("access", access);
        response.addCookie(createCookie("refresh", refresh));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // JSON 변환 후 출력
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(responseBody.getBody()));

    }

    //로그인 실패시 실행하는 메서드
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException failed) throws IOException{
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // 실패 응답 객체 생성
        ResponseEntity<ApiResponse<String>> responseBody = ResponseEntity.badRequest().body(ApiResponse.fail(ResponseCode.BAD_REQUEST, "아이디 혹은 비밀번호를 다시 입력해주세요"));

        // JSON 변환 후 출력
        ObjectMapper objectMapper = new ObjectMapper();
        response.getWriter().write(objectMapper.writeValueAsString(responseBody.getBody()));
    }

    private Cookie createCookie(String key, String value) {

        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24*60*60);
//        cookie.setSecure(true); // https일 경우 설정
//        cookie.setPath("/"); // 쿠키의 적용 범위 설정

        //js로 쿠키에 접근 못하게 함
        cookie.setHttpOnly(true);

        return cookie;
    }
}
