package com.fsd10.merry_match_backend.entity;

import jakarta.persistence.*;
import lombok.*;

// ✅ Lombok จะช่วย generate method พื้นฐานให้เราอัตโนมัติ
@Entity
@Table(name = "tests")
@Data               // ✅ รวม @Getter, @Setter, @ToString, @EqualsAndHashCode
@NoArgsConstructor  // ✅ สร้าง constructor ว่าง (ไม่มี argument)
@AllArgsConstructor // ✅ สร้าง constructor ที่มีทุก field
@Builder            // ✅ ใช้ pattern builder สร้าง object แบบ chain
public class Test {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;     // ✅ id เป็น primary key
    
    @Column(nullable = false)
  private String title;   // ✅ title จะถูก generate getter/setter ให้อัตโนมัติ
  
  @Column(columnDefinition = "TEXT")
  private String content; // ✅ content เช่นเดียวกัน
}
