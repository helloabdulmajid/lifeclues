package in.abdulmajid.lifeclues.testing;

import in.abdulmajid.lifeclues.auth.repository.AuthTokenRepository;
import in.abdulmajid.lifeclues.auth.repository.RefreshTokenRepository;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.memory.repository.CategoryRepository;
import in.abdulmajid.lifeclues.memory.repository.MemoryRepository;
import in.abdulmajid.lifeclues.memory.repository.PersonRepository;
import in.abdulmajid.lifeclues.memory.repository.PlaceRepository;
import in.abdulmajid.lifeclues.memory.repository.TagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
// Each class must discard the Spring context when done: the shared static
// container is stopped and restarted between test classes (with its own fresh
// empty database), whereas a cached context would keep using the previous
// incarnation's DB — and never create the schema on the new one.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected AuthTokenRepository authTokenRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected MemoryRepository memoryRepository;

    @Autowired
    protected TagRepository tagRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @Autowired
    protected PlaceRepository placeRepository;

    @Autowired
    protected PersonRepository personRepository;

    @BeforeEach
    void cleanDatabase() {
        authTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        // memoryRepository first: it owns the many-to-many links to the clue
        // tables, so removing it releases those references before we clear the
        // clue tables themselves (which are the ones hanging off the user row).
        memoryRepository.deleteAllInBatch();
        tagRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        placeRepository.deleteAllInBatch();
        personRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}