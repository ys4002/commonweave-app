package com.commonweave.user;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Batch user lookup to avoid N+1 queries in mappers. */
@Component
public class UserLookup {
    private final UserRepository users;

    public UserLookup(UserRepository users) {
        this.users = users;
    }

    public Map<Long, User> byIds(Collection<Long> ids) {
        return users.findAllById(ids.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    public static UserRef ref(Map<Long, User> map, Long id) {
        User u = map.get(id);
        return u == null ? new UserRef(id, "Former member") : UserRef.of(u);
    }
}
