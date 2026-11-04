package kr.ac.jnu.household;

import java.awt.Font;
import java.awt.event.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.swing.*;

/**
 * 실행 가능한 JAR의 시작 클래스. Java 17 이상, 외부 라이브러리 불필요.
 * @author 김승혁 223247
 * @version 0.4
 */
public final class Main {
    private Main() { }
    /**
     * 프로그램을 실행한다. --demo는 별도 임시 데이터와 고정 날짜를 사용한다.
     * @param args --demo 또는 --data 데이터파일경로
     */
    public static void main(String[] args) {
        boolean demo=Arrays.asList(args).contains("--demo");
        Path path=Path.of(System.getProperty("user.home"),".smart-household","ledger.dat");
        for(int i=0;i<args.length;i++)if(args[i].equals("--data")&&i+1<args.length)path=Path.of(args[++i]);
        final Path chosen=path;
        SwingUtilities.invokeLater(() -> {
            DataRepository repository=null;
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                Font font=new Font("맑은 고딕",Font.PLAIN,13);
                for(Object key:Collections.list(UIManager.getDefaults().keys()))if(UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)UIManager.put(key,new javax.swing.plaf.FontUIResource(font));
                Clock clock=demo?Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"),ZoneId.of("Asia/Seoul")):Clock.systemDefaultZone();
                Path file=demo?Files.createTempDirectory("household-demo-").resolve("ledger.dat"):chosen;
                repository=new DataRepository(file);
                if(demo)repository.commit(DemoData.create());
                AccountService service=new AccountService(repository,clock);
                MainFrame frame=new MainFrame(service,demo);DataRepository finalRepository=repository;
                frame.addWindowListener(new WindowAdapter(){@Override public void windowClosed(WindowEvent e){finalRepository.close();}});
                frame.setVisible(true);
            } catch(Exception e) {
                if(repository!=null)repository.close();
                JOptionPane.showMessageDialog(null,e.getMessage(),"실행 오류",JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
