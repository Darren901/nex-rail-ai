package com.next.nexrailai.jpa.service;

import com.linecorp.bot.messaging.model.UserProfileResponse;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository appUserRepo;

    /**
     * 儲存或更新使用者資訊
     */
    @Transactional
    public void saveOrUpdateUser(UserProfileResponse profile) {
        appUserRepo.findById(profile.userId()).ifPresentOrElse(
                user -> {
                    // 使用者已存在，更新資訊
                    user.setDisplayName(profile.displayName());
                    user.setPictureUrl(String.valueOf(profile.pictureUrl()));
                    user.setStatus(AppUser.UserStatus.PENDING);
                    appUserRepo.save(user);
                    log.info(">>>> [使用者服務] 更新使用者: {} ({})", profile.displayName(), profile.userId());
                },
                () -> {
                    // 新使用者，建立資料
                    AppUser newUser = AppUser.builder()
                            .lineUserId(profile.userId())
                            .displayName(profile.displayName())
                            .pictureUrl(String.valueOf(profile.pictureUrl()))
                            .build();
                    appUserRepo.save(newUser);
                    log.info(">>>> [使用者服務] 新增使用者: {} ({})", profile.displayName(), profile.userId());
                }
        );
    }

    /**
     * 使用者傳送訊息
     *
     * @param userId LINE User ID
     */
    @Transactional
    public void setUserActiveAt(String userId) {
        appUserRepo.findById(userId).ifPresent(appUser -> {
            appUser.setLastActiveAt(LocalDateTime.now());
            appUserRepo.save(appUser);
            log.info(">>>> [使用者服務] 使用者使用者傳訊息了！ : ({})", userId);
        });
    }

    /**
     * 取得使用者狀態
     *
     * @param userId LINE User ID
     * @return UserStatus
     */
    public AppUser.UserStatus getUserStatus(String userId) {
        return appUserRepo.findById(userId)
                .map(AppUser::getStatus)
                .orElse(AppUser.UserStatus.PENDING); // 預設為 PENDING (若找不到使用者)
    }

    /**
     * 使用者把我們封鎖
     *
     * @param userId LINE User ID
     */
    @Transactional
    public void setUserDisable(String userId) {
        appUserRepo.findById(userId).ifPresent(appUser -> {
            appUser.setStatus(AppUser.UserStatus.DISABLE);
            appUserRepo.save(appUser);
            log.info(">>>> [使用者服務] 使用者把我們封鎖了... : ({})", userId);
        });
    }
}
