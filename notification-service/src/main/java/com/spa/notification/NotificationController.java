package com.spa.notification;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
@RestController
public class NotificationController {
 private final NotificationRepository repository;
 public NotificationController(NotificationRepository r) {repository=r;}
 @GetMapping("/api/notifications") public java.util.List<Notification> list() {return repository.findAll(PageRequest.of(0,50,org.springframework.data.domain.Sort.by("createdAt").descending())).getContent();}
}
