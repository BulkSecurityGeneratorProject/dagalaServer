package ir.company.app.service;

import ir.company.app.config.Constants;
import ir.company.app.domain.Authority;
import ir.company.app.domain.entity.*;
import ir.company.app.repository.*;
import ir.company.app.security.AuthoritiesConstants;
import ir.company.app.service.dto.GameLowDTO;
import ir.company.app.service.dto.HomeDTO;
import ir.company.app.service.util.RandomUtil;
import ir.company.app.web.rest.vm.ManagedUserVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service class for managing users.
 */
@Service
@Transactional
public class UserService {

    private final Logger log = LoggerFactory.getLogger(UserService.class);

    @Inject
    private SocialService socialService;

    @Inject
    private PasswordEncoder passwordEncoder;

    @Inject
    private UserRepository userRepository;
    @Inject
    private GameRepository gameRepository;

    @Inject
    private CategoryRepository categoryRepository;
    @Inject
    private ModalRepository modalRepository;

    @Inject
    private LevelRepository levelRepository;

    @Inject
    private AuthorityRepository authorityRepository;

    @PersistenceContext
    private EntityManager em;

    public HomeDTO refresh(boolean newLevel, String username) {
        User user = userRepository.findOneByLogin(username.toLowerCase());
        HomeDTO homeDTO = new HomeDTO();
        homeDTO.score = user.getScore();
        homeDTO.gem = user.getGem();
        homeDTO.level = user.getLevel();
        homeDTO.avatar = user.getAvatar();
        homeDTO.rating = user.getRating();
        homeDTO.coins = user.getCoin();
        homeDTO.newLevel = newLevel;
        homeDTO.user = user.getLogin();
        homeDTO.guest = user.getGuest();
        homeDTO.perGameCoins = Constants.perGame;
        homeDTO.userid = user.getId();
        if (user.getExpireExp() != null && user.getExpireExp().isAfter(ZonedDateTime.now()))
            homeDTO.exp = (user.getExpireExp().toInstant().toEpochMilli() - ZonedDateTime.now().toInstant().toEpochMilli()) / 3600000;
        Query q = em.createNativeQuery("SELECT * FROM (SELECT id,rank() OVER (ORDER BY score DESC) FROM jhi_user ) as gr WHERE  id =?");
        q.setParameter(1, user.getId());
        Object[] o = (Object[]) q.getSingleResult();
        homeDTO.rating = Integer.valueOf(String.valueOf(o[1]));
        List<Game> halfGame = gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.FULL, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id")));
        halfGame.addAll(gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.HALF, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id"))));
        List<Game> fullGame = gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.FINISHED, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id")));
        List<Game> friendly = gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.FRIENDLY, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id")));

        homeDTO.halfGame = new ArrayList<>();
        homeDTO.friendly = new ArrayList<>();
        for (Game game : halfGame) {
            GameLowDTO gameLowDTO = new GameLowDTO();
            GameLowDTO.User firstUser = new GameLowDTO.User();
            GameLowDTO.User secondUser = new GameLowDTO.User();
            gameLowDTO.second = secondUser;
            gameLowDTO.first = firstUser;
            gameLowDTO.gameId = game.getId();


            if (game.getFirst().getLogin().equalsIgnoreCase(username) && game.getSecond() != null) {
                secondUser.user = game.getSecond().getLogin();
                secondUser.avatar = game.getSecond().getAvatar();
                if (game.getFirstScore() > game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "جلویی";
                } else if (game.getFirstScore() < game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "عقبی";

                } else {
                    gameLowDTO.scoreStatus = " مساوی";
                }
            } else if (game.getSecond() != null && game.getSecond().getLogin().equalsIgnoreCase(username)) {

                secondUser.user = game.getFirst().getLogin();
                secondUser.avatar = game.getFirst().getAvatar();
                if (game.getFirstScore() < game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "جلویی";
                } else if (game.getFirstScore() > game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "عقبی";

                } else {
                    gameLowDTO.scoreStatus = " مساوی";
                }
            }
            Challenge challenge = null;
            if (game.getChallenges() != null && game.getChallenges().size() != 0)
                challenge = game.getChallenges().stream().sorted(Comparator.comparingLong(Challenge::getId)).collect(Collectors.toList()).get(game.getChallenges().size() - 1);
            if (challenge == null) {
                if (game.getFirst().getLogin().equalsIgnoreCase(username)) {
                    gameLowDTO.status = "نوبت شماست";

                } else {
                    gameLowDTO.status = "در انتظار حریف";

                }


            } else if (game.getSecond() == null) {
                gameLowDTO.status = "در انتظار حریف";
            } else if (challenge.getSecondScore() != null && (challenge.getFirstScore() == null) && game.getFirst().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "نوبت شماست";

            } else if (challenge.getSecondScore() != null && (challenge.getFirstScore() == null) && !game.getFirst().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "در انتظار حریف";
            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() == null) && game.getSecond().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "نوبت شماست";

            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() == null) && !game.getSecond().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "در انتظار حریف";
            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() != null) && game.getSecond().getLogin().equalsIgnoreCase(username) && game.getChallenges().size() == 1) {
                gameLowDTO.status = "نوبت شماست";

            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() != null) && game.getFirst().getLogin().equalsIgnoreCase(username) && game.getChallenges().size() == 1) {
                gameLowDTO.status = "در انتظار حریف";
            }


            if (game.getChallenges().size() == 2 && (gameLowDTO.status == null || gameLowDTO.status.isEmpty())) {
                gameLowDTO.status = "نوبت شماست";
            }
            if (game.getFirst().getLogin().equalsIgnoreCase(username))
                gameLowDTO.score = game.getSecondScore() + "-" + game.getFirstScore();

            else
                gameLowDTO.score = game.getFirstScore() + "-" + game.getSecondScore();

            homeDTO.halfGame.add(gameLowDTO);
        }

        fillFreindly(username, homeDTO, friendly);

        homeDTO.fullGame = new ArrayList<>();
        for (
            Game game : fullGame)

        {
            GameLowDTO gameLowDTO = new GameLowDTO();
            GameLowDTO.User firstUser = new GameLowDTO.User();
            GameLowDTO.User secondUser = new GameLowDTO.User();
            gameLowDTO.second = secondUser;
            gameLowDTO.first = firstUser;
            gameLowDTO.gameId = game.getId();
            if (game.getFirst().getLogin().equalsIgnoreCase(username))
                gameLowDTO.score = game.getSecondScore() + "-" + game.getFirstScore();

            else
                gameLowDTO.score = game.getFirstScore() + "-" + game.getSecondScore();


            if (username.equalsIgnoreCase(game.getFirst().getLogin())) {
                if (game.getWinner() == 1) {
                    gameLowDTO.status = "بردی";
                } else if (game.getWinner() == 2) {
                    gameLowDTO.status = "باختی";

                } else {
                    gameLowDTO.status = "مساوی";

                }
            } else {
                if (game.getWinner() == 2) {
                    gameLowDTO.status = "بردی";
                } else if (game.getWinner() == 1) {
                    gameLowDTO.status = "باختی";

                } else {
                    gameLowDTO.status = "مساوی";

                }
            }
            fillLowUser(username, game, secondUser);
            homeDTO.fullGame.add(gameLowDTO);
        }
        return homeDTO;
    }

    public HomeDTO refreshV2(boolean newLevel, String username) {
        User user = userRepository.findOneByLogin(username.toLowerCase());
        HomeDTO homeDTO = new HomeDTO();
        homeDTO.score = user.getScore();
        homeDTO.gem = user.getGem();
        homeDTO.level = user.getLevel();
        Level level = levelRepository.findByLevel(user.getLevel() + 1);
        homeDTO.nextLevel = (user.getScore() * level.getThreshold()) / 100;
        homeDTO.avatar = user.getAvatar();
        homeDTO.rating = user.getRating();
        homeDTO.coins = user.getCoin();
        homeDTO.newLevel = newLevel;
        homeDTO.modal = modalRepository.findOne(1l).getContent();
        homeDTO.user = user.getLogin();
        homeDTO.guest = user.getGuest();
        homeDTO.perGameCoins = Constants.perGame;
        homeDTO.userid = user.getId();
        if (user.getExpireExp() != null && user.getExpireExp().isAfter(ZonedDateTime.now()))
            homeDTO.exp = (user.getExpireExp().toInstant().toEpochMilli() - ZonedDateTime.now().toInstant().toEpochMilli()) / 3600000;
        Query q = em.createNativeQuery("SELECT * FROM (SELECT id,rank() OVER (ORDER BY score DESC) FROM jhi_user ) as gr WHERE  id =?");
        q.setParameter(1, user.getId());
        Object[] o = (Object[]) q.getSingleResult();
        homeDTO.rating = Integer.valueOf(String.valueOf(o[1]));
        List<Game> halfGame = gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.FULL, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id")));
        halfGame.addAll(gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.HALF, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id"))));
        List<Game> fullGame = gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.FINISHED, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id")));
        List<Game> friendly = gameRepository.findByGameStatusAndFirstAndSecondAndLeague(GameStatus.FRIENDLY, user, user, new PageRequest(0, 10, new Sort(Sort.Direction.DESC, "id")));

        homeDTO.halfGame = new ArrayList<>();
        homeDTO.friendly = new ArrayList<>();
        for (Game game : halfGame) {
            GameLowDTO gameLowDTO = new GameLowDTO();
            GameLowDTO.User firstUser = new GameLowDTO.User();
            GameLowDTO.User secondUser = new GameLowDTO.User();
            gameLowDTO.second = secondUser;
            gameLowDTO.first = firstUser;
            gameLowDTO.gameId = game.getId();


            if (game.getFirst().getLogin().equalsIgnoreCase(username) && game.getSecond() != null) {
                secondUser.user = game.getSecond().getLogin();
                secondUser.avatar = game.getSecond().getAvatar();
                if (game.getFirstScore() > game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "3";
                } else if (game.getFirstScore() < game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "0";

                } else {
                    gameLowDTO.scoreStatus = " 1";
                }
            } else if (game.getSecond() != null && game.getSecond().getLogin().equalsIgnoreCase(username)) {

                secondUser.user = game.getFirst().getLogin();
                secondUser.avatar = game.getFirst().getAvatar();
                if (game.getFirstScore() < game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "3";
                } else if (game.getFirstScore() > game.getSecondScore()) {
                    gameLowDTO.scoreStatus = "0";

                } else {
                    gameLowDTO.scoreStatus = " 1";
                }
            }
            Challenge challenge = null;
            if (game.getChallenges() != null && game.getChallenges().size() != 0)
                challenge = game.getChallenges().stream().sorted(Comparator.comparingLong(Challenge::getId)).collect(Collectors.toList()).get(game.getChallenges().size() - 1);
            if (challenge == null) {
                if (game.getFirst().getLogin().equalsIgnoreCase(username)) {
                    gameLowDTO.status = "نوبت شماست";

                } else {
                    gameLowDTO.status = "در انتظار حریف";

                }


            } else if (game.getSecond() == null) {
                gameLowDTO.status = "در انتظار حریف";
            } else if (challenge.getSecondScore() != null && (challenge.getFirstScore() == null) && game.getFirst().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "نوبت شماست";

            } else if (challenge.getSecondScore() != null && (challenge.getFirstScore() == null) && !game.getFirst().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "در انتظار حریف";
            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() == null) && game.getSecond().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "نوبت شماست";

            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() == null) && !game.getSecond().getLogin().equalsIgnoreCase(username)) {
                gameLowDTO.status = "در انتظار حریف";
            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() != null) && game.getSecond().getLogin().equalsIgnoreCase(username) && game.getChallenges().size() == 1) {
                gameLowDTO.status = "نوبت شماست";

            } else if (challenge.getFirstScore() != null && (challenge.getSecondScore() != null) && game.getFirst().getLogin().equalsIgnoreCase(username) && game.getChallenges().size() == 1) {
                gameLowDTO.status = "در انتظار حریف";
            }


            if (game.getChallenges().size() == 2 && (gameLowDTO.status == null || gameLowDTO.status.isEmpty())) {
                gameLowDTO.status = "نوبت شماست";
            }
            if (game.getFirst().getLogin().equalsIgnoreCase(username))
                gameLowDTO.score = game.getSecondScore() + "  -  " + game.getFirstScore();

            else
                gameLowDTO.score = game.getFirstScore() + "  -  " + game.getSecondScore();

            homeDTO.halfGame.add(gameLowDTO);
        }

        fillFreindly(username, homeDTO, friendly);

        homeDTO.fullGame = new ArrayList<>();
        for (
            Game game : fullGame)

        {
            GameLowDTO gameLowDTO = new GameLowDTO();
            GameLowDTO.User firstUser = new GameLowDTO.User();
            GameLowDTO.User secondUser = new GameLowDTO.User();
            gameLowDTO.second = secondUser;
            gameLowDTO.first = firstUser;
            gameLowDTO.gameId = game.getId();
            if (game.getFirst().getLogin().equalsIgnoreCase(username))
                gameLowDTO.score = game.getSecondScore() + "-" + game.getFirstScore();

            else
                gameLowDTO.score = game.getFirstScore() + "-" + game.getSecondScore();


            if (username.equalsIgnoreCase(game.getFirst().getLogin())) {
                if (game.getWinner() == 1) {
                    gameLowDTO.status = "3";
                } else if (game.getWinner() == 2) {
                    gameLowDTO.status = "0";

                } else {
                    gameLowDTO.status = "1";

                }
            } else {
                if (game.getWinner() == 2) {
                    gameLowDTO.status = "3";
                } else if (game.getWinner() == 1) {
                    gameLowDTO.status = "0";

                } else {
                    gameLowDTO.status = "1";

                }
            }
            fillLowUser(username, game, secondUser);
            homeDTO.fullGame.add(gameLowDTO);
        }
        return homeDTO;
    }

    private void fillFreindly(String username, HomeDTO homeDTO, List<Game> friendly) {
        for (Game game : friendly) {
            GameLowDTO gameLowDTO = new GameLowDTO();
            GameLowDTO.User firstUser = new GameLowDTO.User();
            GameLowDTO.User secondUser = new GameLowDTO.User();
            gameLowDTO.second = secondUser;
            gameLowDTO.first = firstUser;
            gameLowDTO.gameId = game.getId();

            fillLowUser(username, game, secondUser);
            homeDTO.friendly.add(gameLowDTO);
        }
    }

    private void fillLowUser(String username, Game game, GameLowDTO.User secondUser) {
        if (game.getFirst().getLogin().equalsIgnoreCase(username)) {
            secondUser.user = game.getSecond().getLogin();
            secondUser.avatar = game.getSecond().getAvatar();
        } else {

            secondUser.user = game.getFirst().getLogin();
            secondUser.avatar = game.getFirst().getAvatar();
        }
    }

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        dist = dist * 1.609344;

        return new BigDecimal(dist)
            .setScale(3, BigDecimal.ROUND_HALF_UP).stripTrailingZeros()
            .doubleValue();
    }

    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    /*::	This function converts radians to decimal degrees						 :*/
    /*:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::*/
    private static double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }

    public Optional<User> activateRegistration(String key) {
        log.debug("Activating user for activation key {}", key);
        return userRepository.findOneByActivationKey(key)
            .map(user -> {
                // activate given user for the registration key.
                user.setActivated(true);
                user.setActivationKey(null);
                userRepository.save(user);
                log.debug("Activated user: {}", user);
                return user;
            });
    }

    public Optional<User> completePasswordReset(String newPassword, String key) {
        log.debug("Reset user password for reset key {}", key);

        return userRepository.findOneByResetKey(key)
            .filter(user -> {
                ZonedDateTime oneDayAgo = ZonedDateTime.now().minusHours(24);
                return user.getResetDate().isAfter(oneDayAgo);
            })
            .map(user -> {
                user.setPassword(passwordEncoder.encode(newPassword));
                user.setResetKey(null);
                user.setResetDate(null);
                userRepository.save(user);
                return user;
            });
    }

    public Optional<User> requestPasswordReset(String mail) {
        return userRepository.findOneByEmail(mail)
            .filter(User::isActivated)
            .map(user -> {
                user.setResetKey(RandomUtil.generateResetKey());
                user.setResetDate(ZonedDateTime.now());
                userRepository.save(user);
                return user;
            });
    }

    public User createUser(String login, String password, String firstName, String lastName, String email,
                           String langKey) {

        User newUser = new User();
        Authority authority = authorityRepository.findOne(AuthoritiesConstants.USER);
        List<Authority> authorities = new ArrayList<>();
        String encryptedPassword = passwordEncoder.encode(password);
        newUser.setLogin(login);
        // new user gets initially a generated password
        newUser.setPassword(encryptedPassword);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setEmail(email);
        newUser.setLangKey(langKey);
        // new user is not active
        newUser.setActivated(false);
        // new user gets registration key
        newUser.setActivationKey(RandomUtil.generateActivationKey());
        authorities.add(authority);
//        newUser.setAuthorities(authorities);
        userRepository.save(newUser);
        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    public User createUser(ManagedUserVM managedUserVM) {
        User user = new User();
        user.setLogin(managedUserVM.getLogin());
        user.setFirstName(managedUserVM.getFirstName());
        user.setLastName(managedUserVM.getLastName());
        user.setEmail(managedUserVM.getEmail());
        if (managedUserVM.getLangKey() == null) {
            user.setLangKey("en"); // default language
        } else {
            user.setLangKey(managedUserVM.getLangKey());
        }
        if (managedUserVM.getAuthorities() != null) {
            List<Authority> authorities = new ArrayList<>();
            managedUserVM.getAuthorities().stream().forEach(
                authority -> authorities.add(authorityRepository.findOne(authority))
            );
//            user.setAuthorities(authorities);
        }
        String encryptedPassword = passwordEncoder.encode(RandomUtil.generatePassword());
        user.setPassword(encryptedPassword);
        user.setResetKey(RandomUtil.generateResetKey());
        user.setResetDate(ZonedDateTime.now());
        user.setActivated(true);
        userRepository.save(user);
        log.debug("Created Information for User: {}", user);
        return user;
    }

    public void updateUser(Long id, String login, String firstName, String lastName, String email,
                           boolean activated, String langKey, Set<String> authorities) {

        Optional.of(userRepository
            .findOne(id))
            .ifPresent(u -> {
                u.setLogin(login);
                u.setFirstName(firstName);
                u.setLastName(lastName);
                u.setEmail(email);
                u.setActivated(activated);
                u.setLangKey(langKey);
//                List<Authority> managedAuthorities = u.getAuthorities();
//                managedAuthorities.clear();
//                authorities.stream().forEach(
//                    authority -> managedAuthorities.add(authorityRepository.findOne(authority))
//                );
                log.debug("Changed Information for User: {}", u);
            });
    }


    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthoritiesByLogin(String login) {
//        return userRepository.findOneByLogin(login).map(u -> {
//            u.getAuthorities().size();
//            return u;
//        });
        return null;
    }

    @Transactional(readOnly = true)
    public User getUserWithAuthorities(Long id) {
        User user = userRepository.findOne(id);
//        user.getAuthorities().size(); // eagerly load the association
        return user;
    }


    /**
     * Not activated users should be automatically deleted after 3 days.
     * <p>
     * This is scheduled to get fired everyday, at 01:00 (am).
     * </p>
     */
}
