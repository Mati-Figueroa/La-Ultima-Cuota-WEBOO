package com.ultimacuota.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String username;

    @JsonAlias({"profile_photo", "profilePhoto"})
    private String profilePhoto;

    public void setProfile_photo(String p) { this.profilePhoto = p; }
}
