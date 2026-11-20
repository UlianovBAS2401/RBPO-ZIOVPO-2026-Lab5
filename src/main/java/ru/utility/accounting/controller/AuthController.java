package ru.utility.accounting.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.utility.accounting.dto.LoginRequest;
import ru.utility.accounting.dto.RefreshRequest;
import ru.utility.accounting.dto.RegisterRequest;
import ru.utility.accounting.dto.TokenResponse;
import ru.utility.accounting.dto.UserDto;
import ru.utility.accounting.entity.User;
import ru.utility.accounting.repository.UserRepository;
import ru.utility.accounting.security.PasswordValidator;
import ru.utility.accounting.service.TokenService;

import java.time.OffsetDateTime;

@RestController
@RequestMapping({"/auth", "/api/auth"})
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          PasswordValidator passwordValidator,
                          TokenService tokenService,
                          AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.tokenService = tokenService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (LockedException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Учётная запись заблокирована администратором");
        } catch (DisabledException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Учётная запись деактивирована");
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные учётные данные при входе");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверные учётные данные при входе"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Учётная запись заблокирована или неактивна");
        }

        TokenResponse tokenResponse = tokenService.issueTokenPair(user, request.getDeviceId());
        response.setHeader("Authorization", "Bearer " + tokenResponse.getAccessToken());
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        passwordValidator.validate(request.getPassword());

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин '" + request.getUsername() + "' уже занят");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email '" + request.getEmail() + "' уже зарегистрирован");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole("ROLE_USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);

        TokenResponse tokenResponse = tokenService.issueTokenPair(savedUser, request.getDeviceId());
        response.setHeader("Authorization", "Bearer " + tokenResponse.getAccessToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletResponse response) {
        TokenResponse tokenResponse = tokenService.refreshTokenPair(request.getRefreshToken());
        response.setHeader("Authorization", "Bearer " + tokenResponse.getAccessToken());
        return ResponseEntity.ok(tokenResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не аутентифицирован");
        }
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));

        UserDto dto = new UserDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
        return ResponseEntity.ok(dto);
    }
}
