package in.abdulmajid.lifeclues.testing;

import in.abdulmajid.lifeclues.auth.repository.AuthTokenRepository;
import in.abdulmajid.lifeclues.auth.repository.RefreshTokenRepository;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.memory.repository.MemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
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

    @BeforeEach
    void cleanDatabase() {
        authTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        memoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}