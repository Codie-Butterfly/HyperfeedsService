package zw.co.hyperfeeds.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(name = "phone_number", nullable = false, unique = true) String phoneNumber;
    @Column(name = "first_name", nullable = false) String firstName;
    @Column(name = "last_name", nullable = false) String lastName;
    @Column(name = "password_hash") String passwordHash;
    @Column(name = "phone_verified", nullable = false) boolean phoneVerified;
    @Column(nullable = false) boolean employee;
    @Column(nullable = false) boolean active = true;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false) Instant updatedAt;

    protected User() {}
    User(String phoneNumber, String firstName, String lastName) {
        this.phoneNumber = phoneNumber; this.firstName = firstName; this.lastName = lastName;
    }
}
