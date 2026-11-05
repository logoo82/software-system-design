package household;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.YearMonth;
import household.Domain.State;
import static household.AppException.Code.*;

/**
 * 가계부 내용을 파일에서 불러오고 저장한다.
 * 같은 파일을 동시에 쓰지 않게 하고, 저장에 실패하면 원래 내용을 남겨둔다.
 * @author 김승혁
 * @version 0.4
 */
public class DataRepository implements AutoCloseable {
    private final Path file;
    private FileChannel lockChannel;
    private FileLock lock;

    /**
     * 저장 위치를 준비하고 다른 실행에서 같은 파일을 쓰는지 확인한다.
     * @param file 데이터 파일
     * @throws AppException 이미 사용 중인 파일이거나 저장 위치를 만들 수 없는 경우
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

            // 같은 저장 파일을 다른 실행에서 이미 쓰고 있으면 중단한다.
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
     * 전에 저장한 내용을 불러온다. 첫 실행이면 빈 목록으로 시작한다.
     * 파일에 문제가 있으면 새 내용으로 덮어쓰지 않고 오류를 알린다.
     * @param start 처음 기록을 시작할 달
     * @return 불러온 가계부 내용
     * @throws AppException 파일을 읽지 못하거나 저장 형식이 다른 경우
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
                        // 폴더를 정리하기 전에 저장한 파일도 계속 읽을 수 있게 한다.
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
     * 새 내용을 다른 파일에 먼저 적고, 끝나면 기존 저장 파일을 바꾼다.
     * @param state 이번에 저장할 가계부 내용
     * @throws AppException 파일을 쓰거나 안전하게 교체하지 못한 경우
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

            // 새 파일 쓰기가 끝난 뒤에만 원래 저장 파일을 바꾼다.
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

    /** 종료할 때 다른 실행에서도 파일을 사용할 수 있게 닫아준다. */
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
