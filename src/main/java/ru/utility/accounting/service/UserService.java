package ru.utility.accounting.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.utility.accounting.dto.UserDto;
import ru.utility.accounting.entity.Meter;
import ru.utility.accounting.entity.Premises;
import ru.utility.accounting.entity.User;
import ru.utility.accounting.repository.*;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PremisesRepository premisesRepository;
    private final MeterRepository meterRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UtilityChargeRepository utilityChargeRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PremisesRepository premisesRepository,
                       MeterRepository meterRepository,
                       MeterReadingRepository meterReadingRepository,
                       UtilityChargeRepository utilityChargeRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.premisesRepository = premisesRepository;
        this.meterRepository = meterRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.utilityChargeRepository = utilityChargeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден с id: " + id));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден с логином: " + username));
    }

    @Transactional
    public User create(User user, String rawPassword) {
        user.setCreatedAt(OffsetDateTime.now());

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username обязателен");
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username '" + user.getUsername() + "' уже занят");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email обязателен");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email '" + user.getEmail() + "' уже занят");
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ФИО обязательно");
        }

        String pass = (rawPassword != null && !rawPassword.isBlank()) ? rawPassword : "User@12345";
        user.setPassword(passwordEncoder.encode(pass));

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("ROLE_USER");
        }
        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("ACTIVE");
        }

        return userRepository.save(user);
    }

    @Transactional
    public User patch(Long id, UserDto dto) {
        User user = findById(id);

        if (dto.getFullName() != null && !dto.getFullName().isBlank()) {
            user.setFullName(dto.getFullName());
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (!dto.getEmail().equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email '" + dto.getEmail() + "' уже занят");
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }

        return userRepository.save(user);
    }

    @Transactional
    public User updateStatus(Long id, String newStatus) {
        User user = findById(id);
        if (newStatus == null || (!newStatus.equals("ACTIVE") && !newStatus.equals("BLOCKED") && !newStatus.equals("SUSPENDED"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный статус. Допустимые: ACTIVE, BLOCKED, SUSPENDED");
        }
        user.setStatus(newStatus);
        return userRepository.save(user);
    }

    @Transactional
    public User updateRole(Long id, String newRole) {
        User user = findById(id);
        if (newRole == null || (!newRole.equals("ROLE_USER") && !newRole.equals("ROLE_OPERATOR") && !newRole.equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректная роль. Допустимые: ROLE_USER, ROLE_OPERATOR, ROLE_ADMIN");
        }
        user.setRole(newRole);
        return userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findById(id);

        List<Premises> premisesList = premisesRepository.findByUserId(user.getId());
        for (Premises premises : premisesList) {
            utilityChargeRepository.deleteByPremisesId(premises.getId());

            List<Meter> meters = meterRepository.findByPremisesId(premises.getId());
            for (Meter meter : meters) {
                meterReadingRepository.deleteByMeterId(meter.getId());
                meterRepository.delete(meter);
            }

            premisesRepository.delete(premises);
        }

        userRepository.delete(user);
    }
}
