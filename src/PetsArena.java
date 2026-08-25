import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.*;
import java.io.IOException;
import java.util.Random;

public class PetsArena extends MIDlet {
    private GameScreen gameScreen;

    public void startApp() {
        if (gameScreen == null) {
            gameScreen = new GameScreen();
            Display.getDisplay(this).setCurrent(gameScreen);
            new Thread(gameScreen).start();
        }
    }

    public void pauseApp() {}
    public void destroyApp(boolean unconditional) {}

    class GameScreen extends GameCanvas implements Runnable {
        private boolean running = true;
        
        // Nokia C3-00 Orijinal Ekran Boyutu: 320x240 (Yatay)
        private static final int SCREEN_W = 320;
        private static final int SCREEN_H = 240;

        // Oyuncu Değişkenleri
        private int playerX = 20, playerY = 160;
        private int velX = 0, velY = 0;
        private int playerW = 16, playerH = 16;
        private boolean grounded = false;
        private int facingDir = 1;

        // Karakterler (Sıralama: Tristan, Grimace, Halley, Babs, Junie, Rossi, Stevon, Cookie, Avery)
        private String[] charNames = {"Tristan", "Grimace", "Halley", "Babs", "Junie", "Rossi", "Stevon", "Cookie", "Avery"};
        private int[] charColors = {0x10b981, 0xf43f5e, 0x38bdf8, 0xeab308, 0x3b82f6, 0xf97316, 0xec4899, 0x8b5cf6, 0x14b8a6};
        private int[] charSpeeds = {5, 4, 6, 4, 5, 5, 4, 5, 4};
        private int[] charJumps = {-13, -14, -12, -13, -12, -13, -12, -12, -13};
        private Image[] charImages = new Image[9];
        private int activeCharIndex = 0;
        private int unlockedCharIndex = 0;

        // Oyun İlerlemesi ve HUD
        private int currentLevel = 1;
        private int totalCoins = 0;
        private int levelTimer = 25;
        private long lastTimerTime = 0;
        private boolean levelPieceCollected = false;
        private Image dotPieceImg;

        // Platform Yapıları (320x240 Yatay Ölçülere Göre)
        private int[][] platforms = new int[6][9];
        private int[] dotPiece = new int[4]; 
        private int[] portal = new int[4];   
        private int[][] chocolates = new int[4][3]; 

        // Efektler
        private int screenShakeFrames = 0;
        private Random random = new Random();

        public GameScreen() {
            super(true);
            loadImages();
            generateLevel(currentLevel);
        }

        private void loadImages() {
            for (int i = 0; i < charNames.length; i++) {
                try {
                    charImages[i] = Image.createImage("/" + (i + 1) + ".png");
                } catch (IOException e) {
                    charImages[i] = null; 
                }
            }
            try {
                dotPieceImg = Image.createImage("/DotPiece.png");
            } catch (IOException e) {
                dotPieceImg = null;
            }
        }

        private void generateLevel(int lvl) {
            random.setSeed(lvl * 1543L);
            levelPieceCollected = false;
            levelTimer = 25;
            lastTimerTime = System.currentTimeMillis();

            // Başlangıç Platformu (Sol Alt)
            platforms[0] = new int[]{0, 200, 70, 40, 0, 0, 0, 0, 0};

            int currentX = 80;
            int currentY = 160;

            for (int i = 1; i < 5; i++) {
                int pWidth = 40 + Math.abs(random.nextInt() % 20);
                int pType = 0;
                int rType = Math.abs(random.nextInt() % 100);
                if (lvl > 20 && rType > 60) pType = 1; 
                if (lvl > 50 && rType <= 30) pType = 2; 

                platforms[i] = new int[]{currentX, currentY, pWidth, 10, pType, 0, 0, currentX, 1};

                if (Math.abs(random.nextInt() % 100) > 40) {
                    chocolates[i - 1] = new int[]{currentX + (pWidth / 2) - 4, currentY - 15, 0};
                } else {
                    chocolates[i - 1] = new int[]{-50, -50, 1};
                }

                if (i == 2) {
                    dotPiece[0] = currentX + (pWidth / 2) - 6;
                    dotPiece[1] = currentY - 20;
                    dotPiece[2] = 12;
                    dotPiece[3] = 12;
                }

                currentX += 50 + Math.abs(random.nextInt() % 30);
                currentY += (random.nextInt() % 40) - 20;
                if (currentY < 80) currentY = 80;
                if (currentY > 190) currentY = 190;
            }

            // Bitiş Platformu ve Portal (Sağ Alt)
            platforms[5] = new int[]{260, 200, 60, 40, 0, 0, 0, 0, 0};
            portal[0] = 280; portal[1] = 160; portal[2] = 20; portal[3] = 40;
        }

        public void run() {
            Graphics g = getGraphics();
            while (running) {
                update();
                draw(g);
                flushGraphics();
                try { Thread.sleep(30); } catch (InterruptedException e) {}
            }
        }

        private void update() {
            if (System.currentTimeMillis() - lastTimerTime >= 1000) {
                levelTimer--;
                lastTimerTime = System.currentTimeMillis();
                if (levelTimer <= 0) handleDeath();
            }

            int keyStates = getKeyStates();

            // Fiziksel tuşlar ve D-pad kontrolleri
            if ((keyStates & LEFT_PRESSED) != 0 || (keyStates & KEY_NUM4) != 0) {
                velX = -charSpeeds[activeCharIndex];
                facingDir = -1;
            } else if ((keyStates & RIGHT_PRESSED) != 0 || (keyStates & KEY_NUM6) != 0) {
                velX = charSpeeds[activeCharIndex];
                facingDir = 1;
            } else {
                velX = 0;
            }

            if (((keyStates & UP_PRESSED) != 0 || (keyStates & KEY_NUM2) != 0) && grounded) {
                velY = charJumps[activeCharIndex];
                grounded = false;
            }

            // Karakter Değiştirme (Yıldız Tuşu *)
            if ((keyStates & KEY_STAR) != 0) {
                activeCharIndex++;
                if (activeCharIndex > unlockedCharIndex || activeCharIndex >= charNames.length) {
                    activeCharIndex = 0;
                }
            }

            velY += 1; 
            playerX += velX;
            playerY += velY;
            grounded = false;

            for (int i = 0; i < platforms.length; i++) {
                int[] p = platforms[i];
                if (p[6] == 1) continue;

                if (p[4] == 2) {
                    p[0] += p[8] * 2;
                    if (p[0] > p[7] + 30 || p[0] < p[7] - 30) p[8] *= -1;
                }

                if (p[4] == 1 && p[5] > 0) {
                    if (System.currentTimeMillis() - p[5] > 800) p[6] = 1;
                }

                if (playerX + playerW > p[0] && playerX < p[0] + p[2] &&
                    playerY + playerH <= p[1] + 8 && playerY + playerH + velY >= p[1]) {
                    
                    playerY = p[1] - playerH;
                    velY = 0;
                    grounded = true;

                    if (p[4] == 1 && p[5] == 0) p[5] = (int)System.currentTimeMillis();
                    if (p[4] == 2) playerX += p[8] * 2;
                }
            }

            for (int i = 0; i < chocolates.length; i++) {
                if (chocolates[i][2] == 0) {
                    if (playerX < chocolates[i][0] + 8 && playerX + playerW > chocolates[i][0] &&
                        playerY < chocolates[i][1] + 8 && playerY + playerH > chocolates[i][1]) {
                        chocolates[i][2] = 1;
                        totalCoins++;
                        levelTimer += 3;
                    }
                }
            }

            if (!levelPieceCollected && playerX < dotPiece[0] + dotPiece[2] && playerX + playerW > dotPiece[0] &&
                playerY < dotPiece[1] + dotPiece[3] && playerY + playerH > dotPiece[1]) {
                levelPieceCollected = true;
                screenShakeFrames = 4;
            }

            if (levelPieceCollected && playerX < portal[0] + portal[2] && playerX + playerW > portal[0] &&
                playerY < portal[1] + portal[3] && playerY + playerH > portal[1]) {
                
                if (currentLevel < 1000) {
                    currentLevel++;
                    unlockedCharIndex = Math.min((currentLevel - 1) / 100, 8);
                    generateLevel(currentLevel);
                    playerX = 20; playerY = 160; velY = 0;
                    screenShakeFrames = 6;
                }
            }

            if (playerY > SCREEN_H + 20) handleDeath();
        }

        private void handleDeath() {
            screenShakeFrames = 8;
            playerX = 20; playerY = 160; velX = 0; velY = 0;
            generateLevel(currentLevel);
        }

        private void draw(Graphics g) {
            int offsetX = 0, offsetY = 0;
            if (screenShakeFrames > 0) {
                offsetX = (random.nextInt() % 4);
                offsetY = (random.nextInt() % 4);
                screenShakeFrames--;
            }

            // Arka Plan
            g.setColor(0x022c22);
            g.fillRect(0, 0, SCREEN_W, SCREEN_H);

            // Platformlar
            for (int i = 0; i < platforms.length; i++) {
                int[] p = platforms[i];
                if (p[6] == 1) continue;

                if (p[4] == 0) g.setColor(0x047857);      
                else if (p[4] == 1) g.setColor(0xb45309); 
                else if (p[4] == 2) g.setColor(0x1d4ed8); 

                g.fillRect(p[0] + offsetX, p[1] + offsetY, p[2], p[3]);
            }

            // Çikolatalar
            g.setColor(0xf1c40f);
            for (int i = 0; i < chocolates.length; i++) {
                if (chocolates[i][2] == 0) {
                    g.fillRect(chocolates[i][0] + offsetX, chocolates[i][1] + offsetY, 8, 8);
                }
            }

            // Parça
            if (!levelPieceCollected) {
                if (dotPieceImg != null) {
                    g.drawImage(dotPieceImg, dotPiece[0] + offsetX, dotPiece[1] + offsetY, Graphics.TOP | Graphics.LEFT);
                } else {
                    g.setColor(0x00ffff);
                    g.fillArc(dotPiece[0] + offsetX, dotPiece[1] + offsetY, 12, 12, 0, 360);
                }
            }

            // Portal
            g.setColor(levelPieceCollected ? 0x8b5cf6 : 0x4b5563);
            g.fillRect(portal[0] + offsetX, portal[1] + offsetY, portal[2], portal[3]);

            // Karakter
            if (charImages[activeCharIndex] != null) {
                g.drawImage(charImages[activeCharIndex], playerX + offsetX, playerY + offsetY, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(charColors[activeCharIndex]);
                g.fillRect(playerX + offsetX, playerY + offsetY, playerW, playerH);
            }

            // Temiz HUD (Üst Kısım)
            g.setColor(0x10b981);
            g.drawString("LVL:" + currentLevel + " PET:" + charNames[activeCharIndex] + "(*)", 5, 3, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xf59e0b);
            g.drawString("SÜRE:" + levelTimer + "s | ÇİKO:" + totalCoins, 5, 18, Graphics.TOP | Graphics.LEFT);
        }
    }
        }
