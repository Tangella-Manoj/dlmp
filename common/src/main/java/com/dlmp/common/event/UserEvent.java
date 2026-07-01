package com.dlmp.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserEvent extends DomainEvent {

    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;

    public static UserEvent of(String type, String userId, String email,
                                String firstName, String traceId) {
        UserEvent e = UserEvent.builder()
                .userId(userId).email(email).firstName(firstName)
                .eventType(type).build();
        init(e, userId, "USER", traceId);
        return e;
    }
}
