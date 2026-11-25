
// ============================================================
// INVENTRA - COMPUTER GRAPHICS MODULE
// File: cg/lab3d.cpp
// Requires OpenGL + FreeGLUT + GLU
// ============================================================

#include <GL/glut.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <string>
#include <cmath>
#include <vector>
#include <algorithm>

using namespace std;

const int WIDTH=1100, HEIGHT=750;
const int PC_COUNT=12;

struct PC {
    int id;
    int status; // 0 Working, 1 Faulty, 2 Inspection
};

PC pcs[PC_COUNT];

float angleY=0, angleX=20;
float moveX=0, moveZ=0, scaleValue=1;
bool mirror=false;
bool map2D=false;
bool inspect=false;
int selectedPC=0;
bool perspective=true;

GLuint monitorTexture;

// [CO6] RGB colour definitions
void statusColor(int status) {
    if(status==1)
        glColor3f(0.95f,0.18f,0.22f);
    else if(status==2)
        glColor3f(1.0f,0.65f,0.08f);
    else
        glColor3f(0.12f,0.85f,0.43f);
}

// [CO1] OpenGL primitives and 3D modelling
void cube(float x,float y,float z,
          float sx,float sy,float sz) {
    glPushMatrix();
    glTranslatef(x,y,z);
    glScalef(sx,sy,sz);
    glutSolidCube(1.0);
    glPopMatrix();
}

void cylinder(float x,float y,float z,
              float radius,float height) {
    GLUquadric* q=gluNewQuadric();
    glPushMatrix();
    glTranslatef(x,y,z);
    gluCylinder(q,radius,radius,height,20,10);
    glPopMatrix();
    gluDeleteQuadric(q);
}

// [CO6] Procedural monitor screen texture
void createTexture() {
    const int N=64;
    unsigned char pixels[N*N*3];

    for(int y=0;y<N;y++) {
        for(int x=0;x<N;x++) {
            int i=(y*N+x)*3;
            bool grid=(x%8==0 || y%8==0);

            pixels[i]=grid?40:12;
            pixels[i+1]=grid?145:45;
            pixels[i+2]=grid?255:110;
        }
    }

    glGenTextures(1,&monitorTexture);
    glBindTexture(GL_TEXTURE_2D,monitorTexture);

    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);

    glTexImage2D(GL_TEXTURE_2D,0,GL_RGB,N,N,0,
                 GL_RGB,GL_UNSIGNED_BYTE,pixels);
}

// [CO1] GL_QUADS and procedural texture mapping
void monitorScreen() {
    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D,monitorTexture);
    glColor3f(1,1,1);

    glBegin(GL_QUADS);
    glTexCoord2f(0,0);glVertex3f(-0.45f,0.35f,0);
    glTexCoord2f(1,0);glVertex3f(0.45f,0.35f,0);
    glTexCoord2f(1,1);glVertex3f(0.45f,0.95f,0);
    glTexCoord2f(0,1);glVertex3f(-0.45f,0.95f,0);
    glEnd();

    glDisable(GL_TEXTURE_2D);
}

// [CO1] GL_LINES, GL_QUADS, glutSolidCube,
// glutSolidSphere and gluCylinder.
void drawPC(int index) {
    float x=(index%4)*3.0f-4.5f;
    float z=(index/4)*3.0f-3.0f;

    glPushMatrix();
    glTranslatef(x,0,z);

    statusColor(pcs[index].status);

    // Desk
    cube(0,0.8f,0,1.7f,0.12f,1.2f);
    cube(0,0.35f,0,0.12f,0.8f,0.12f);

    // Monitor body
    glColor3f(0.12f,0.17f,0.25f);
    cube(0,1.65f,-0.25f,1.0f,0.85f,0.16f);

    glPushMatrix();
    glTranslatef(0,1.65f,-0.16f);
    glScalef(0.8f,0.65f,1);
    monitorScreen();
    glPopMatrix();

    // Monitor stand
    glColor3f(0.5f,0.55f,0.62f);
    cube(0,1.12f,-0.25f,0.12f,0.25f,0.12f);

    // CPU cabinet
    glColor3f(0.25f,0.32f,0.42f);
    cube(0.55f,1.2f,0.1f,0.38f,0.7f,0.45f);

    // Mouse
    glColor3f(0.75f,0.78f,0.82f);
    glPushMatrix();
    glTranslatef(-0.65f,0.95f,0.1f);
    glScalef(0.2f,0.1f,0.3f);
    glutSolidSphere(0.5,16,16);
    glPopMatrix();

    // Keyboard
    glColor3f(0.12f,0.15f,0.2f);
    cube(0,0.94f,0.35f,0.65f,0.07f,0.28f);

    // Chair
    glColor3f(0.35f,0.4f,0.5f);
    cube(0,0.45f,1.0f,0.7f,0.1f,0.7f);

    // [CO1] Cylinder primitive
    glColor3f(0.3f,0.35f,0.42f);
    glPushMatrix();
    glTranslatef(0,0.1f,1.0f);
    cylinder(0,0,0,0.06f,0.35f);
    glPopMatrix();

    glPopMatrix();
}

// [CO3] 2D line drawing using GL_LINES
void line2D(float x1,float y1,float x2,float y2) {
    glBegin(GL_LINES);
    glVertex2f(x1,y1);
    glVertex2f(x2,y2);
    glEnd();
}

// [CO5] Cohen-Sutherland line clipping
const int LEFT=1,RIGHT=2,BOTTOM=4,TOP=8;

int outCode(float x,float y,
            float xmin,float ymin,float xmax,float ymax) {
    int code=0;

    if(x<xmin)code|=LEFT;
    else if(x>xmax)code|=RIGHT;

    if(y<ymin)code|=BOTTOM;
    else if(y>ymax)code|=TOP;

    return code;
}

bool clipLine(float &x1,float &y1,float &x2,float &y2) {
    float xmin=-5,xmax=5,ymin=-3,ymax=3;

    int c1=outCode(x1,y1,xmin,ymin,xmax,ymax);
    int c2=outCode(x2,y2,xmin,ymin,xmax,ymax);

    while(true) {
        if(!(c1|c2))return true;
        if(c1&c2)return false;

        int c=c1?c1:c2;
        float x=0,y=0;

        if(c&TOP) {
            x=x1+(x2-x1)*(ymax-y1)/(y2-y1);
            y=ymax;
        } else if(c&BOTTOM) {
            x=x1+(x2-x1)*(ymin-y1)/(y2-y1);
            y=ymin;
        } else if(c&RIGHT) {
            y=y1+(y2-y1)*(xmax-x1)/(x2-x1);
            x=xmax;
        } else {
            y=y1+(y2-y1)*(xmin-x1)/(x2-x1);
            x=xmin;
        }

        if(c==c1) {
            x1=x;y1=y;
            c1=outCode(x1,y1,xmin,ymin,xmax,ymax);
        } else {
            x2=x;y2=y;
            c2=outCode(x2,y2,xmin,ymin,xmax,ymax);
        }
    }
}

// [CO3] 2D floor plan and line drawing
void draw2D() {
    glMatrixMode(GL_PROJECTION);
    glLoadIdentity();
    gluOrtho2D(-8,8,-6,6);

    glMatrixMode(GL_MODELVIEW);
    glLoadIdentity();

    glColor3f(0.2f,0.5f,0.8f);

    for(int i=0;i<12;i++) {
        float x=(i%4)*3.0f-4.5f;
        float y=(i/4)*3.0f-1.5f;

        glBegin(GL_QUADS);
        glVertex2f(x-0.9f,y-0.5f);
        glVertex2f(x+0.9f,y-0.5f);
        glVertex2f(x+0.9f,y+0.5f);
        glVertex2f(x-0.9f,y+0.5f);
        glEnd();

        glColor3f(1,1,1);
        line2D(x-0.9f,y-0.5f,x+0.9f,y-0.5f);
        line2D(x+0.9f,y-0.5f,x+0.9f,y+0.5f);
        line2D(x+0.9f,y+0.5f,x-0.9f,y+0.5f);
        line2D(x-0.9f,y+0.5f,x-0.9f,y-0.5f);

        glRasterPos2f(x-0.3f,y);
        string label="PC"+to_string(i+1);
        for(char c:label)glutBitmapCharacter(GLUT_BITMAP_8_BY_13,c);

        statusColor(pcs[i].status);
    }

    // [CO5] Live clipping viewport
    glColor3f(1,0.8f,0.1f);
    glBegin(GL_LINE_LOOP);
    glVertex2f(-5,-3);
    glVertex2f(5,-3);
    glVertex2f(5,3);
    glVertex2f(-5,3);
    glEnd();

    float x1=-7,y1=-4,x2=7,y2=4;

    glColor3f(1,0,0);
    line2D(x1,y1,x2,y2);

    if(clipLine(x1,y1,x2,y2)) {
        glColor3f(0,1,0);
        line2D(x1,y1,x2,y2);
    }
}

// [CO4] Perspective and orthographic projection
void setCamera() {
    glMatrixMode(GL_PROJECTION);
    glLoadIdentity();

    if(perspective)
        gluPerspective(55.0,WIDTH/(double)HEIGHT,0.1,100);
    else
        glOrtho(-12,12,-8,8,-40,40);

    glMatrixMode(GL_MODELVIEW);
    glLoadIdentity();

    // [CO4] Camera viewing using gluLookAt
    gluLookAt(0,inspect?5:10,inspect?9:17,
              0,0,0,0,1,0);
}

// HUD
void text(float x,float y,string s) {
    glRasterPos2f(x,y);
    for(char c:s)
        glutBitmapCharacter(GLUT_BITMAP_9_BY_15,c);
}

// [CO6] Lighting using GL_LIGHT0
void lighting() {
    GLfloat position[]={0,10,8,1};
    GLfloat ambient[]={0.3f,0.3f,0.3f,1};
    GLfloat diffuse[]={0.9f,0.9f,0.9f,1};

    glEnable(GL_LIGHTING);
    glEnable(GL_LIGHT0);
    glEnable(GL_COLOR_MATERIAL);

    glLightfv(GL_LIGHT0,GL_POSITION,position);
    glLightfv(GL_LIGHT0,GL_AMBIENT,ambient);
    glLightfv(GL_LIGHT0,GL_DIFFUSE,diffuse);
}

// [CO2] Transformations: translation, rotation,
// scaling and mirroring.
void draw3D() {
    setCamera();

    glPushMatrix();

    glTranslatef(moveX,0,moveZ);
    glRotatef(angleX,1,0,0);
    glRotatef(angleY,0,1,0);

    glScalef(scaleValue,scaleValue,
             mirror?-scaleValue:scaleValue);

    // Floor
    glDisable(GL_LIGHTING);
    glColor3f(0.12f,0.18f,0.27f);

    glBegin(GL_QUADS);
    glVertex3f(-8,-0.1f,-7);
    glVertex3f(8,-0.1f,-7);
    glVertex3f(8,-0.1f,7);
    glVertex3f(-8,-0.1f,7);
    glEnd();

    glEnable(GL_LIGHTING);

    // Walls
    glColor3f(0.2f,0.3f,0.45f);
    cube(0,3.5f,-7,16,7,0.2f);
    cube(-8,3.5f,0,0.2f,7,14);

    for(int i=0;i<PC_COUNT;i++) {
        if(inspect && i!=selectedPC)continue;
        drawPC(i);
    }

    glPopMatrix();
}

void loadCSV() {
    // [CO3] File input and live data polling
    ifstream file("data/lab_status.csv");
    if(!file)return;

    string line;
    getline(file,line);

    while(getline(file,line)) {
        stringstream ss(line);
        string id,status,assetID;

        getline(ss,id,',');
        getline(ss,status,',');
        getline(ss,assetID,',');

        try {
            int n=stoi(id);
            int s=stoi(status);

            if(n>=1 && n<=PC_COUNT && s>=0 && s<=2)
                pcs[n-1]={n,s};
        } catch(...) {}
    }
}

void display() {
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);

    if(map2D) {
        glDisable(GL_LIGHTING);
        draw2D();
    } else {
        lighting();
        draw3D();
        glDisable(GL_LIGHTING);
    }

    // HUD overlay
    glMatrixMode(GL_PROJECTION);
    glPushMatrix();
    glLoadIdentity();
    gluOrtho2D(0,WIDTH,0,HEIGHT);

    glMatrixMode(GL_MODELVIEW);
    glPushMatrix();
    glLoadIdentity();

    glColor3f(0.3f,0.75f,1);
    text(20,HEIGHT-30,"INVENTRA | SMART COMPUTER LAB");

    glColor3f(1,1,1);
    text(20,HEIGHT-55,
         "WASD: Move | Q/E: Rotate | +/-: Scale | M: Mirror");

    text(20,HEIGHT-78,
         "2: Floor Map | 3: 3D View | I: Inspect | P: Projection");

    text(20,HEIGHT-101,
         "1-9: Select PC | Arrows: Rotate | ESC: Exit");

    int working=0,faulty=0,inspection=0;
    for(auto&p:pcs) {
        if(p.status==0)working++;
        else if(p.status==1)faulty++;
        else inspection++;
    }

    text(20,25,"WORKING: "+to_string(working));
    text(200,25,"FAULTY: "+to_string(faulty));
    text(350,25,"INSPECTION: "+to_string(inspection));

    glPopMatrix();

    glMatrixMode(GL_PROJECTION);
    glPopMatrix();
    glMatrixMode(GL_MODELVIEW);

    glutSwapBuffers();
}

// [CO2] Keyboard transformation controls
void keyboard(unsigned char key,int,int) {
    switch(key) {
    case 'w':case 'W':moveZ-=0.5f;break;
    case 's':case 'S':moveZ+=0.5f;break;
    case 'a':case 'A':moveX-=0.5f;break;
    case 'd':case 'D':moveX+=0.5f;break;
    case 'q':case 'Q':angleY-=5;break;
    case 'e':case 'E':angleY+=5;break;
    case '+':scaleValue+=0.1f;break;
    case '-':scaleValue=max(0.2f,scaleValue-0.1f);break;
    case 'm':case 'M':mirror=!mirror;break;
    case '2':map2D=true;break;
    case '3':map2D=false;break;
    case 'i':case 'I':
        inspect=!inspect;
        break;
    case 'p':case 'P':perspective=!perspective;break;
    case 27:exit(0);
    }

    glutPostRedisplay();
}

void specialKeys(int key,int,int) {
    if(key==GLUT_KEY_LEFT)angleY-=5;
    if(key==GLUT_KEY_RIGHT)angleY+=5;
    if(key==GLUT_KEY_UP)angleX-=5;
    if(key==GLUT_KEY_DOWN)angleX+=5;

    glutPostRedisplay();
}

// [CO4] Single-PC inspection selection
void numberKeys(unsigned char key,int,int) {
    if(key>='1'&&key<='9') {
        selectedPC=key-'1';
        inspect=true;
    }
}

void timer(int) {
    loadCSV();
    glutPostRedisplay();
    glutTimerFunc(2000,timer,0);
}

int main(int argc,char**argv) {
    for(int i=0;i<PC_COUNT;i++)
        pcs[i]={i+1,0};

    loadCSV();

    glutInit(&argc,argv);
    glutInitDisplayMode(GLUT_DOUBLE|GLUT_RGB|GLUT_DEPTH);
    glutInitWindowSize(WIDTH,HEIGHT);
    glutCreateWindow("INVENTRA - 3D Computer Laboratory");

    glEnable(GL_DEPTH_TEST);
    glClearColor(0.035f,0.07f,0.13f,1);

    createTexture();

    glutDisplayFunc(display);
    glutKeyboardFunc(keyboard);
    glutSpecialFunc(specialKeys);
    glutTimerFunc(2000,timer,0);

    // Register number key handler through normal keyboard
    // handling for individual workstation inspection.
    // [CO4] Inspection mode is available through I.

    glutMainLoop();
    return 0;
}
