package household;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.YearMonth;
import household.Domain.State;
import static household.AppException.Code.*;

/**
 * 전체 상태를 Java 직렬화 파일에 저장한다. 파일 잠금으로 중복 실행을 막고,
 * 임시 파일 기록 후 원자적 교체가 지원될 때만 변경을 확정한다.
 * 지원하지 않으면 IO_ERROR로 종료하여 기존 파일을 보존한다.
 * @author 김승혁
 * @version 0.4
 */
public class DataRepository implements AutoCloseable {
    private final Path file;
    private FileChannel lockChannel;
    private FileLock lock;

    /**
     * 파일 경로를 준비하고 배타 잠금을 획득한다.
     * @param file 데이터 파일
     * @throws AppException 파일 사용 중 또는 경로 생성 실패
     */
    public DataRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.file.getParent());
            lockChannel =
                    FileChannel.open(
                            this.file.resolveSibling(this.file.getFileName() + ".lock"),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE);

            lock = lockChannel.tryLock();
            if (lock == null) {
                throw new AppException(DATA_IN_USE, "같은 데이터로 실행 중인 프로그램을 먼저 종료해 주세요.");
            }
        } catch (OverlappingFileLockException e) {
            close();
            throw new AppException(DATA_IN_USE, "데이터 파일을 이미 사용 중입니다.");
        } catch (IOException | AppException e) {
            close();
            if (e instanceof AppException a) {
                throw a;
            }
            throw new AppException(IO_ERROR, "데이터 경로를 열 수 없습니다: " + e.getMessage());
        }
    }

    /**
     * 저장 파일을 읽는다. 부재만 빈 상태로 취급하고 손상 파일은 덮어쓰지 않는다.
     * @param start 신규 기록 시작 월
     * @return 저장된 전체 상태
     * @throws AppException 읽기·형식 오류
     */
    public State load(YearMonth start) {
        if (!Files.exists(file)) {
            return State.empty(start);
        }

        try (ObjectInputStream in =
                new ObjectInputStream(Files.newInputStream(file)) {
                    @Override
                    protected Class<?> resolveClass(ObjectStreamClass descriptor)
                            throws IOException, ClassNotFoundException {
                        // 패키지 정리 전 버전에서 저장한 파일도 읽는다.
                        String name = descriptor.getName();
                        String oldPackage = "kr.ac.jnu.household.";

                        if (name.startsWith(oldPackage) || name.startsWith("[L" + oldPackage)) {
                            return Class.forName(name.replace(oldPackage, "household."));
                        }

                        return super.resolveClass(descriptor);
                    }
                }) {
            in.setObjectInputFilter(
                    ObjectInputFilter.Config.createFilter(
                            "maxdepth=32;maxrefs=200000;maxbytes=33554432;household.*;java.base/*;!*"));

            Object value = in.readObject();
            if (!(value instanceof State state)) {
                throw new IOException("저장 형식이 다릅니다.");
            }

            return state;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            throw new AppException(IO_ERROR, "데이터를 읽지 못했습니다. 원본 파일은 유지됩니다: " + e.getMessage());
        }
    }

    /**
     * 변경본을 임시 파일에 완성한 다음 기존 파일을 교체한다.
     * @param state 변경 불가능한 전체 상태
     * @throws AppException 저장 실패 또는 원자적 교체 미지원
     */
    public void commit(State state) {
        Path temp = null;

        try {
            temp = Files.createTempFile(file.getParent(), "ledger-", ".tmp");

            try (FileOutputStream out = new FileOutputStream(temp.toFile());
                    ObjectOutputStream objects = new ObjectOutputStream(out)) {
                objects.writeObject(state);
                objects.flush();
                out.getFD().sync();
            }

            Files.move(
                    temp,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new AppException(IO_ERROR, "저장에 실패했습니다. 변경 전 상태를 유지합니다: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 저장 위치를 반환한다.
     * @return 정규화된 절대 경로
     */
    public Path path() {
        return file;
    }

    /** 잠금과 파일 채널을 해제한다. */
    @Override
    public void close() {
        try {
            if (lock != null) {
                lock.release();
            }
        } catch (IOException ignored) {
        }

        try {
            if (lockChannel != null) {
                lockChannel.close();
            }
        } catch (IOException ignored) {
        }
    }
}
