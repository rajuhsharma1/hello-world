package com.spa.operations;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
public class Staff {
 @Id public UUID id;
 public String name;
 public String role;
 public String contactNumber;
 public String address;
 public java.time.LocalDate joinedOn;
 protected Staff() {}
 Staff(String name,String role) {id=UUID.randomUUID();this.name=name;this.role=role;}
}
