package com.tririvenord.preference;

import com.tririvenord.municipality.Municipality;
import com.tririvenord.municipality.MunicipalityService;
import com.tririvenord.preference.dto.UserPreferenceRequest;
import org.springframework.stereotype.Service;

@Service
public class UserPreferenceService {
    private final UserPreferenceMapper mapper;
    private final MunicipalityService municipalities;

    public UserPreferenceService(UserPreferenceMapper mapper, MunicipalityService municipalities) {
        this.mapper = mapper;
        this.municipalities = municipalities;
    }

    public UserPreference getForUser(Long userId) {
        return mapper.findByUserId(userId)
                .orElseGet(() -> {
                    insertDefaultFor(userId);
                    return mapper.findByUserId(userId).orElseThrow();
                });
    }

    /**
     * The row a new account starts with: the default city and a sector that
     * city actually has.
     *
     * <p>Both used to be constants -- no city at all, and the literal
     * 'north'. With four cities the first violates NOT NULL and the second
     * names a place three of them do not have.
     */
    public void insertDefaultFor(Long userId) {
        Municipality city = municipalities.defaultMunicipality();
        mapper.insertDefault(userId, city.id(), municipalities.defaultSectorOf(city));
    }

    public UserPreference update(Long userId, UserPreferenceRequest request) {
        Municipality city = municipalities.requireBySlug(request.municipality());
        // Rejected here rather than stored and failing later: a sector the
        // city does not have is not a preference, it is a 404.
        municipalities.requireSector(city, request.sector());
        mapper.update(userId, city.id(), request.sector(),
                request.languageCode(), request.reminderEnabled());
        return mapper.findByUserId(userId).orElseThrow();
    }
}
