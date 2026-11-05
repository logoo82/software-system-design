package household;

import java.awt.Font;
import java.awt.event.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.swing.*;

/**
 * 프로그램을 시작하고 저장소, 기능 처리, 화면을 연결한다.
 * @author 김승혁 223247
 * @version 0.4
 */
public final class Main {
    private Main() {}

    /**
     * 일반 실행인지 데모인지 확인한 뒤 가계부 화면을 연다.
     * @param args 데모 실행(--demo) 또는 저장 위치 지정(--data 경로)
     */
    public static void main(String[] args) {
        // 일반 실행인지 예시 데이터로 실행하는 건지 먼저 확인한다.
        boolean demo = Arrays.asList(args).contains("--demo");
        Path path = Path.of(System.getProperty("user.home"), ".smart-household", "ledger.dat");

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--data") && i + 1 < args.length) {
                path = Path.of(args[++i]);
            }
        }
        final Path chosen = path;

        SwingUtilities.invokeLater(() -> {
            DataRepository repository = null;

            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());

                // 한글이 잘 보이도록 화면 글꼴을 맞춰준다.
                Font font = new Font("맑은 고딕", Font.PLAIN, 13);
                for (Object key : Collections.list(UIManager.getDefaults().keys())) {
                    if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) {
                        UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
                    }
                }

                // 데모는 보고서와 같은 날짜로 계산하고, 일반 실행은 오늘 날짜를 쓴다.
                Clock clock =
                        demo
                                ? Clock.fixed(
                                        Instant.parse("2026-10-15T03:00:00Z"),
                                        ZoneId.of("Asia/Seoul"))
                                : Clock.systemDefaultZone();
                Path file =
                        demo
                                ? Files.createTempDirectory("household-demo-").resolve("ledger.dat")
                                : chosen;
                repository = new DataRepository(file);
                if (demo) {
                    repository.commit(DemoData.create());
                }

                // 저장소와 기능 처리를 연결한 다음 화면을 연다.
                AccountService service = new AccountService(repository, clock);
                MainFrame frame = new MainFrame(service, demo);

                DataRepository finalRepository = repository;
                frame.addWindowListener(
                        new WindowAdapter() {
                            @Override
                            public void windowClosed(WindowEvent e) {
                                finalRepository.close();
                            }
                        });

                frame.setVisible(true);
            } catch (Exception e) {
                if (repository != null) {
                    repository.close();
                }

                JOptionPane.showMessageDialog(
                        null, e.getMessage(), "실행 오류", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
