import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Kelas untuk merepresentasikan satu aturan IF-THEN dari kb_ayam.json
class Aturan {
    String id;
    List<String> jika;
    String mode; // "dan" atau "atau"
    String maka;
    String sumber;

    public Aturan(String id, List<String> jika, String mode, String maka, String sumber) {
        this.id = id;
        this.jika = jika;
        this.mode = mode;
        this.maka = maka;
        this.sumber = sumber;
    }
}

// Kelas untuk menyimpan jejak alasan sebuah kesimpulan
class JejakAlasan {
    Aturan aturan;
    List<String> syaratTerpenuhi;

    public JejakAlasan(Aturan aturan, List<String> syaratTerpenuhi) {
        this.aturan = aturan;
        this.syaratTerpenuhi = syaratTerpenuhi;
    }
}

public class SistemPakarAyamGUI extends JFrame {
    private String judul = "Sistem Pakar Kondisi Ayam";
    private String batasKeberlakuan = "";
    private List<Aturan> daftarAturan = new ArrayList<>();
    private List<String> gejalaDasar = new ArrayList<>();

    private List<String> faktaAwal = new ArrayList<>();
    private Set<String> memori = new LinkedHashSet<>();
    private int indeksPertanyaan = 0;

    // Komponen GUI
    private JLabel lblJudul;
    private JLabel lblProgress;
    private JLabel lblPertanyaan;
    private JButton btnYa;
    private JButton btnTidak;
    private JButton btnUlangi;
    private JTextArea txtLog;

    public SistemPakarAyamGUI() {
        boolean sukses = bacaFileAturan("kb_ayam.json");
        setupGUI();
        if (sukses) {
            tampilkanPertanyaan();
        } else {
            lblPertanyaan.setText("Gagal membaca file kb_ayam.json!");
            btnYa.setEnabled(false);
            btnTidak.setEnabled(false);
        }
    }

    // Membaca dan mem-parsing kb_ayam.json tanpa library eksternal
    private boolean bacaFileAturan(String kb_ayam) {
        try {
            String isi = Files.readString(Paths.get(kb_ayam), StandardCharsets.UTF_8);

            // Ambil judul dan batas keberlakuan
            Matcher mJudul = Pattern.compile("\"judul\"\\s*:\\s*\"([^\"]+)\"").matcher(isi);
            if (mJudul.find()) {
                judul = mJudul.group(1);
            }

            Matcher mBatas = Pattern.compile("\"batas\"\\s*:\\s*\"([^\"]+)\"").matcher(isi);
            if (mBatas.find()) {
                batasKeberlakuan = mBatas.group(1);
            }

            // Ambil blok array "aturan": [ ... ]
            Matcher mBlok = Pattern.compile("\"aturan\"\\s*:\\s*\\[(.*)\\]", Pattern.DOTALL).matcher(isi);
            if (!mBlok.find()) {
                return false;
            }
            String blokAturan = mBlok.group(1);

            // Parse setiap objek { ... } di dalam array aturan
            Matcher mObjek = Pattern.compile("\\{[^{}]+\\}").matcher(blokAturan);
            while (mObjek.find()) {
                String obj = mObjek.group();
                String id = ambilField(obj, "id");
                String mode = ambilField(obj, "mode").toLowerCase();
                String maka = ambilField(obj, "maka").toLowerCase();
                String sumber = ambilField(obj, "sumber");
                List<String> jika = ambilArray(obj, "jika");

                daftarAturan.add(new Aturan(id, jika, mode, maka, sumber));
            }

            // Ekstrak fakta dasar (syarat di "jika" yang tidak pernah menjadi "maka")
            Set<String> semuaMaka = new HashSet<>();
            for (Aturan a : daftarAturan) {
                semuaMaka.add(a.maka);
            }

            for (Aturan a : daftarAturan) {
                for (String syarat : a.jika) {
                    if (!semuaMaka.contains(syarat) && !gejalaDasar.contains(syarat)) {
                        gejalaDasar.add(syarat);
                    }
                }
            }

            return !daftarAturan.isEmpty();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                "File '" + kb_ayam + "' tidak ditemukan di folder program.\nPastikan kb_ayam.json berada di folder yang sama.",
                "Error File Aturan",
                JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
    }

    private String ambilField(String teks, String kunci) {
        Matcher m = Pattern.compile("\"" + kunci + "\"\\s*:\\s*\"([^\"]+)\"").matcher(teks);
        return m.find() ? m.group(1) : "";
    }

    private List<String> ambilArray(String teks, String kunci) {
        List<String> hasil = new ArrayList<>();
        Matcher mArr = Pattern.compile("\"" + kunci + "\"\\s*:\\s*\\[([^\\]]+)\\]").matcher(teks);
        if (mArr.find()) {
            Matcher mItem = Pattern.compile("\"([^\"]+)\"").matcher(mArr.group(1));
            while (mItem.find()) {
                hasil.add(mItem.group(1).toLowerCase());
            }
        }
        return hasil;
    }

    private void setupGUI() {
        setTitle(judul);
        setSize(680, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        setLocationRelativeTo(null);

        getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Panel Atas: Judul, Progress, dan Pertanyaan
        JPanel panelAtas = new JPanel(new GridLayout(3, 1, 5, 5));
        lblJudul = new JLabel(judul, SwingConstants.CENTER);
        lblJudul.setFont(new Font("Arial", Font.BOLD, 16));

        lblProgress = new JLabel("", SwingConstants.CENTER);
        lblProgress.setFont(new Font("Arial", Font.ITALIC, 12));
        lblProgress.setForeground(Color.GRAY);

        lblPertanyaan = new JLabel("Memuat pertanyaan...", SwingConstants.CENTER);
        lblPertanyaan.setFont(new Font("Arial", Font.PLAIN, 15));

        panelAtas.add(lblJudul);
        panelAtas.add(lblProgress);
        panelAtas.add(lblPertanyaan);
        add(panelAtas, BorderLayout.NORTH);

        // Panel Tengah: Tombol Ya / Tidak / Ulangi
        JPanel panelTombol = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        btnYa = new JButton("Ya");
        btnTidak = new JButton("Tidak");
        btnUlangi = new JButton("Ulangi");

        btnYa.setPreferredSize(new Dimension(110, 36));
        btnTidak.setPreferredSize(new Dimension(110, 36));
        btnUlangi.setPreferredSize(new Dimension(110, 36));

        panelTombol.add(btnYa);
        panelTombol.add(btnTidak);
        panelTombol.add(btnUlangi);
        add(panelTombol, BorderLayout.CENTER);

        // Panel Bawah: Area Log Proses, Kesimpulan, Alasan, dan Catatan
        txtLog = new JTextArea(19, 55);
        txtLog.setEditable(false);
        txtLog.setLineWrap(true);
        txtLog.setWrapStyleWord(true);
        txtLog.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(txtLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Hasil & Log Forward Chaining"));
        add(scrollPane, BorderLayout.SOUTH);

        // Event Listener
        btnYa.addActionListener(e -> prosesJawaban(true));
        btnTidak.addActionListener(e -> prosesJawaban(false));
        btnUlangi.addActionListener(e -> resetProgram());
    }

    private void tampilkanPertanyaan() {
        if (indeksPertanyaan < gejalaDasar.size()) {
            lblProgress.setText("Pertanyaan " + (indeksPertanyaan + 1) + " dari " + gejalaDasar.size());
            lblPertanyaan.setForeground(Color.BLACK);
            lblPertanyaan.setFont(new Font("Arial", Font.PLAIN, 15));
            lblPertanyaan.setText("Apakah " + gejalaDasar.get(indeksPertanyaan) + "?");
        } else {
            lblProgress.setText("Analisis selesai.");
            btnYa.setEnabled(false);
            btnTidak.setEnabled(false);
            jalankanForwardChaining();
        }
    }

    private void prosesJawaban(boolean isYa) {
        String gejala = gejalaDasar.get(indeksPertanyaan);
        String labelJawab = isYa ? "y" : "t";
        txtLog.append((indeksPertanyaan + 1) + ". Apakah " + gejala + "? -> " + labelJawab + "\n");

        if (isYa) {
            faktaAwal.add(gejala);
            memori.add(gejala);
        }

        indeksPertanyaan++;
        tampilkanPertanyaan();
    }

    private void jalankanForwardChaining() {
        txtLog.append("\nFakta awal: " + (faktaAwal.isEmpty() ? "tidak ada" : String.join(", ", faktaAwal)) + "\n\n");

        int siklus = 1;
        Map<String, JejakAlasan> alasanMap = new LinkedHashMap<>();
        List<String> faktaHasil = new ArrayList<>();
        Set<String> faktaTerpakaiLagi = new HashSet<>();

        // Loop inti Forward Chaining
        while (true) {
            Aturan aturanCocok = null;
            List<String> syaratCocok = new ArrayList<>();

            for (Aturan aturan : daftarAturan) {
                // Jangan jalankan aturan yang hasilnya sudah ada di memori
                if (memori.contains(aturan.maka)) {
                    continue;
                }

                List<String> terpenuhi = new ArrayList<>();
                for (String s : aturan.jika) {
                    if (memori.contains(s)) {
                        terpenuhi.add(s);
                    }
                }

                boolean cocok = false;
                if (aturan.mode.equals("dan") && terpenuhi.size() == aturan.jika.size()) {
                    cocok = true;
                } else if (aturan.mode.equals("atau") && !terpenuhi.isEmpty()) {
                    cocok = true;
                }

                if (cocok) {
                    aturanCocok = aturan;
                    syaratCocok = terpenuhi;
                    break; // Ambil aturan pertama yang cocok
                }
            }

            if (aturanCocok == null) {
                txtLog.append("Siklus " + siklus + ": tidak ada aturan baru, berhenti\n");
                break;
            }

            memori.add(aturanCocok.maka);
            faktaHasil.add(aturanCocok.maka);
            alasanMap.put(aturanCocok.maka, new JejakAlasan(aturanCocok, syaratCocok));

            for (String s : syaratCocok) {
                faktaTerpakaiLagi.add(s);
            }

            txtLog.append("Siklus " + siklus + ": " + aturanCocok.id + " -> " + aturanCocok.maka + "\n");
            siklus++;
        }

        // Kesimpulan akhir = hasil inferensi yang tidak lagi dipakai sebagai syarat aturan lain
        List<String> kesimpulanAkhir = new ArrayList<>();
        for (String hasil : faktaHasil) {
            if (!faktaTerpakaiLagi.contains(hasil)) {
                kesimpulanAkhir.add(hasil);
            }
        }

        txtLog.append("\n");
        if (kesimpulanAkhir.isEmpty()) {
            lblPertanyaan.setText("Kesimpulan: Tidak ada kesimpulan yang terpenuhi.");
            lblPertanyaan.setForeground(new Color(180, 60, 0));
            lblPertanyaan.setFont(new Font("Arial", Font.BOLD, 14));

            txtLog.append("Kesimpulan: tidak ada\n");
            txtLog.append("Alasan:\n  - Fakta yang diberikan belum memenuhi aturan mana pun.\n");
        } else {
            String teksKesimpulan = String.join(", ", kesimpulanAkhir);
            lblPertanyaan.setText("Kesimpulan: " + teksKesimpulan.toUpperCase());
            lblPertanyaan.setForeground(new Color(0, 85, 165));
            lblPertanyaan.setFont(new Font("Arial", Font.BOLD, 14));

            txtLog.append("Kesimpulan: " + teksKesimpulan + "\n");
            txtLog.append("Alasan:\n");
            for (String k : kesimpulanAkhir) {
                cetakAlasan(k, alasanMap, "  ");
            }
        }

        // Wajib tampilkan batas keberlakuan di program
        txtLog.append("\nCatatan: " + batasKeberlakuan + "\n");
    }

    // Mencetak jejak aturan secara rekursif (pohon alasan)
    private void cetakAlasan(String fakta, Map<String, JejakAlasan> alasanMap, String indent) {
        JejakAlasan jejak = alasanMap.get(fakta);
        if (jejak == null) {
            return;
        }
        String syaratStr = String.join(", ", jejak.syaratTerpenuhi);
        txtLog.append(indent + fakta + " <- " + jejak.aturan.id + " (" + syaratStr + ") [" + jejak.aturan.sumber + "]\n");

        for (String s : jejak.syaratTerpenuhi) {
            if (alasanMap.containsKey(s)) {
                cetakAlasan(s, alasanMap, indent + "  ");
            }
        }
    }

    private void resetProgram() {
        faktaAwal.clear();
        memori.clear();
        indeksPertanyaan = 0;
        txtLog.setText("");
        btnYa.setEnabled(true);
        btnTidak.setEnabled(true);
        tampilkanPertanyaan();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SistemPakarAyamGUI gui = new SistemPakarAyamGUI();
            gui.setVisible(true);
        });
    }
}
