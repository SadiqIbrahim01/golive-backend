package com.golivebackend.security;

import com.golivebackend.admin.model.Admin;
import com.golivebackend.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * UserDetailsService that loads administrative credentials from the database.
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;

    /**
     * Loads the admin user from the database by username.
     *
     * @param username the username from the login request
     * @return UserDetails with ROLE_ADMIN authority and database BCrypt-hashed password
     * @throws UsernameNotFoundException if username is not found in database
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found in database: " + username));

        return User.builder()
                .username(admin.getUsername())
                .password(admin.getPassword()) // Already BCrypt-hashed in database
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
    }
}
