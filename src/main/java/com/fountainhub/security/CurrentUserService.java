package com.fountainhub.security;

import com.fountainhub.entity.User;
import com.fountainhub.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 「当前登录的是谁」的唯一入口（R-007）。
 *
 * 【为什么要有它】
 *   在这之前，取当前用户有两种写法，散落在各处：
 *     ProfileController 自己写了一个 currentUser()
 *     GlobalModelAdvice  直接写 findByUsername(authentication.getName())
 *   现在统一到这里，语义清楚，改也只需要改一处。
 *
 * 【🔒 安全铁律（CODE_MAP 第 9 节第 7 条）】
 *   当前用户**只能从会话（Authentication）取**，**绝不允许从 URL 参数取**！
 *   从会话取 → 天然只能操作自己 →「改不了别人的资料」这条验收就是这么实现的。
 *   **本类刻意不提供"按 id 取用户"的方法**，就是为了不给人留下犯错的口子。
 */
@Component
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 是不是游客（没登录）。
     *
     * ⚠️ 别用 `authentication.isAuthenticated()` 判断！
     *   Spring Security 给游客也套了一个 `AnonymousAuthenticationToken`，
     *   它的 `isAuthenticated()` 返回的也是 **true**。
     */
    public boolean isAnonymous(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken;
    }

    /** 从会话取当前用户；没登录或查不到时返回空 */
    public Optional<User> find(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(authentication.getName());
    }

    /**
     * 从会话取当前用户；取不到就抛错。
     *
     * 【为什么抛错比"静默返回 null"安全】
     *   "已经登录却查不到这个用户"说明数据不一致，是真正的异常状态。
     *   静默继续会让后面的代码拿到 null，报出更难懂的错误。
     */
    public User require(Authentication authentication) {
        return find(authentication).orElseThrow(() -> new IllegalStateException(
                "已登录却查不到用户：" + (authentication == null ? "(无)" : authentication.getName())));
    }
}
