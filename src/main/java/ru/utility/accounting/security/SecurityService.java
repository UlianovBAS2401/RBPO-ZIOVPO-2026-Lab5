package ru.utility.accounting.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.utility.accounting.entity.*;
import ru.utility.accounting.repository.*;

import java.util.Optional;

@Service("securityService")
@Transactional(readOnly = true)
public class SecurityService {

    private final UserRepository userRepository;
    private final PremisesRepository premisesRepository;
    private final MeterRepository meterRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UtilityChargeRepository utilityChargeRepository;

    public SecurityService(UserRepository userRepository,
                           PremisesRepository premisesRepository,
                           MeterRepository meterRepository,
                           MeterReadingRepository meterReadingRepository,
                           UtilityChargeRepository utilityChargeRepository) {
        this.userRepository = userRepository;
        this.premisesRepository = premisesRepository;
        this.meterRepository = meterRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.utilityChargeRepository = utilityChargeRepository;
    }

    private Optional<User> getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username);
    }

    public boolean isUserSelf(Authentication authentication, Long userId) {
        if (userId == null) {
            return false;
        }
        return getAuthenticatedUser(authentication)
                .map(u -> u.getId().equals(userId))
                .orElse(false);
    }

    public boolean isPremisesOwner(Authentication authentication, Long premisesId) {
        if (premisesId == null) {
            return false;
        }
        return getAuthenticatedUser(authentication)
                .flatMap(u -> premisesRepository.findById(premisesId)
                        .map(p -> p.getUser() != null && p.getUser().getId().equals(u.getId())))
                .orElse(false);
    }

    public boolean isMeterOwner(Authentication authentication, Long meterId) {
        if (meterId == null) {
            return false;
        }
        return getAuthenticatedUser(authentication)
                .flatMap(u -> meterRepository.findById(meterId)
                        .map(m -> m.getPremises() != null && m.getPremises().getUser() != null && m.getPremises().getUser().getId().equals(u.getId())))
                .orElse(false);
    }

    public boolean isReadingOwner(Authentication authentication, Long readingId) {
        if (readingId == null) {
            return false;
        }
        return getAuthenticatedUser(authentication)
                .flatMap(u -> meterReadingRepository.findById(readingId)
                        .map(r -> r.getMeter() != null && r.getMeter().getPremises() != null &&
                                  r.getMeter().getPremises().getUser() != null &&
                                  r.getMeter().getPremises().getUser().getId().equals(u.getId())))
                .orElse(false);
    }

    public boolean isChargeOwner(Authentication authentication, Long chargeId) {
        if (chargeId == null) {
            return false;
        }
        return getAuthenticatedUser(authentication)
                .flatMap(u -> utilityChargeRepository.findById(chargeId)
                        .map(c -> c.getPremises() != null && c.getPremises().getUser() != null &&
                                  c.getPremises().getUser().getId().equals(u.getId())))
                .orElse(false);
    }
}
