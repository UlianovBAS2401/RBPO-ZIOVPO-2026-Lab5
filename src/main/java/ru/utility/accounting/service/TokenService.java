package ru.utility.accounting.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.utility.accounting.dto.TokenResponse;
import ru.utility.accounting.entity.SessionStatus;
import ru.utility.accounting.entity.User;
import ru.utility.accounting.entity.UserSession;
import ru.utility.accounting.repository.UserRepository;
import ru.utility.accounting.repository.UserSessionRepository;
import ru.utility.accounting.security.JwtService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class TokenService {

    private final JwtService jwtService;
    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public TokenService(JwtService jwtService,
                        UserSessionRepository sessionRepository,
                        UserRepository userRepository) {
        this.jwtService = jwtService;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TokenResponse issueTokenPair(User user, String deviceId) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Учётная запись не активна или заблокирована");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant accessExpiry = now.plusSeconds(jwtService.getAccessTokenExpirationSeconds());
        Instant refreshExpiry = now.plusSeconds(jwtService.getRefreshTokenExpirationSeconds());

        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole(), now, accessExpiry);
        String refreshToken = jwtService.generateRefreshToken(user.getUsername(), now, refreshExpiry);

        UserSession session = new UserSession();
        session.setUsername(user.getUsername());
        session.setDeviceId(deviceId);
        session.setAccessToken(accessToken);
        session.setRefreshToken(refreshToken);
        session.setAccessTokenExpiry(accessExpiry);
        session.setRefreshTokenExpiry(refreshExpiry);
        session.setStatus(SessionStatus.ACTIVE);

        sessionRepository.save(session);

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public TokenResponse refreshTokenPair(String refreshTokenStr) {
        if (refreshTokenStr == null || refreshTokenStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поле refreshToken обязательно");
        }

        Claims claims;
        try {
            claims = jwtService.validateRefreshToken(refreshTokenStr);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Неверный, просроченный или неподходящий по типу refresh: " + ex.getMessage());
        }

        String sub = claims.getSubject();

        Optional<UserSession> sessionOpt = sessionRepository.findByRefreshTokenForUpdate(refreshTokenStr);
        if (sessionOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неизвестная refresh-сессия");
        }

        UserSession session = sessionOpt.get();

        if (session.getStatus() == SessionStatus.USED) {
            if (session.getRefreshTokenExpiry().isAfter(Instant.now())) {
                session.setStatus(SessionStatus.REVOKED);
                sessionRepository.saveAndFlush(session);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Повторное предъявление использованного refresh-токена: сессия отозвана");
            } else {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Использованная refresh-сессия истекла");
            }
        }

        if (session.getStatus() == SessionStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Отозванная refresh-сессия");
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неактивная refresh-сессия");
        }

        if (!session.getRefreshTokenExpiry().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Срок действия refresh-сессии истёк");
        }

        if (!session.getUsername().equals(sub)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Субъект токена не соответствует владельцу сессии");
        }

        User user = userRepository.findByUsername(session.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Учётная запись заблокирована или неактивна");
        }

        session.setStatus(SessionStatus.USED);
        sessionRepository.save(session);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant newAccessExpiry = now.plusSeconds(jwtService.getAccessTokenExpirationSeconds());
        Instant newRefreshExpiry = now.plusSeconds(jwtService.getRefreshTokenExpirationSeconds());

        String newAccessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole(), now, newAccessExpiry);
        String newRefreshToken = jwtService.generateRefreshToken(user.getUsername(), now, newRefreshExpiry);

        UserSession newSession = new UserSession();
        newSession.setUsername(user.getUsername());
        newSession.setDeviceId(session.getDeviceId());
        newSession.setAccessToken(newAccessToken);
        newSession.setRefreshToken(newRefreshToken);
        newSession.setAccessTokenExpiry(newAccessExpiry);
        newSession.setRefreshTokenExpiry(newRefreshExpiry);
        newSession.setStatus(SessionStatus.ACTIVE);

        sessionRepository.save(newSession);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}
