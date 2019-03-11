package com.dji.uxsdkdemo;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayDeque;
import java.util.ArrayList;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static org.opencv.core.Core.FONT_HERSHEY_SIMPLEX;
import static org.opencv.imgproc.Imgproc.putText;
import static org.opencv.imgproc.Imgproc.rectangle;

public class Detection {
    private static final String TAG = "Detection";
    private Mat mOut;
    private Mat mIntermediate;
    private Mat probHoughLines;//// Probabilistic Line Transform

    //box principal
    //Rect2d lastBox;
    //Rect2d currentBox;
    //Rect2d BoxSavedTowerTop;
    //Rect2d BoxSavedTowerBody;

    class Feature {
        int angleRaw;
        int classId;
        int numberOfLines;
        double x0, y0, x1, y1;//quadrilatero da Feature
    }

    class TwoPoints {
        double x0;
        double y0;
        double x1;
        double y1;
    }

    //private ArrayList<Feature> line45 = new ArrayList();


    public Mat preProcessing(Mat inputFrame) {
        //mRgba = inputFrame.rgba();
        Mat out = new Mat(inputFrame.height(),inputFrame.width(), CvType.CV_8UC4);
        mIntermediate = new Mat(inputFrame.height(),inputFrame.width(), CvType.CV_8UC4);
        probHoughLines = new Mat(inputFrame.height(),inputFrame.width(), CvType.CV_8UC4);
        //mIntermediateMat = inputFrame.rgba();

        //mRgba = new Mat(inputframe.getHeight()  height,inputFrame.getWidth() width, CvType.CV_8UC4);
        //Imgproc.Canny(inputFrame.gray(), mIntermediateMat, 80, 100);
        //Imgproc.cvtColor(inputFrame.gray(), mRgba, Imgproc.COLOR_GRAY2RGBA, 4);

        Imgproc.blur(inputFrame, mIntermediate, new Size(3, 3));
        Imgproc.Canny(mIntermediate, out, 80, 100);

        Mat linesP = new Mat(); // will hold the results of the detection
        Imgproc.HoughLinesP(out, linesP, 1, Math.PI/180, 50, 50, 10); // runs the actual detection //três últimos números 54,12,9
        traceLinesPlus(probHoughLines,linesP);
        // Draw the lines
       /* for (int x = 0; x < linesP.rows(); x++) {
            double[] l = linesP.get(x, 0);
            Imgproc.line(probHoughLines, new Point(l[0], l[1]), new Point(l[2], l[3]), new Scalar(0, 0, 255), 1, Imgproc.LINE_AA, 0);
        }*/
       // veja https://docs.opencv.org/3.4.0/d9/db0/tutorial_hough_lines.html
        return probHoughLines;
    }

    private void traceLines(Mat dest, Mat lines)
    {
        for (int x = 0; x < lines.rows(); x++) {
            double[] l = lines.get(x, 0);
            Imgproc.line(probHoughLines, new Point(l[0], l[1]), new Point(l[2], l[3]), new Scalar(0, 255, 0), 1, Imgproc.LINE_AA, 0);
        }
    }

    private void traceLinesPlus(Mat dest, Mat lines)//lines é uma matriz mas contem somente retas vindas da transformada de Hough
    {
       double temp;
       double[] angle = new double[lines.rows()];
       int[] angleRaw = new int[lines.rows()];
       int[] classifier = new int[lines.rows()];
       //useColorLine = new Scalar(255, 255, 0);
       int red;
       int green;
       int blue;


        // ArrayList<Double> angle = new ArrayList<Double>(lines.rows());
      // ArrayList<Integer> angleRaw = new ArrayList<Integer>(lines.rows());


        for (int i = 0; i < lines.rows(); i++) {
            double[] l = lines.get(i, 0);
            //cor default
            red=0;
            green=255;
            blue=0;

            temp = (atan2(l[3] - l[1], l[0] - l[2]) * 180) / 3.14159265; //angle = atan2 (y,x) * 180 / PI; usei l[3]-l[1] porque na tela o y cresce para baixo, e no gráfico cartesiano para cima. De qualquer forma afoi deste jeito que os ângulos ficaram coerente com a apresentação na tela
            Log.i("Angle A=", Double.toString(temp));

            if (temp < 0) angle[i] = temp + 180; //substitui valores como -135° por 45°
            else angle[i] = temp;
            Log.i("Angle B=", Double.toString(angle[i]));

            //de 179º a 181º  e 0º a 1º (não tem não tem negativo)
            if ((angle[i] >= 179) && (angle[i] <= 181)) { angleRaw[i] = 180; red=0; green=0; blue=255;}//Azul
            if ((angle[i] >= 0) && (angle[i] <= 1)) { angleRaw[i] = 180; red=0; green=0; blue=255;}//Azul

            //85°, 90°, 95° linhas verticais - torre
            if ((angle[i] >= 80) && (angle[i] < 89)) { angleRaw[i] = 85; red=255; green=0; blue=255; }//Left leg
            if ((angle[i] >= 89) && (angle[i] <= 91)) { angleRaw[i] = 90; red=255; green=200; blue=255; }
            if ((angle[i] > 91) && (angle[i] <= 100)) { angleRaw[i] = 95; red=255; green=0; blue=0; }//Right leg

            //45° e 135° linha diagonais - treliça (desloquei o range para 125 a 155 ficou melhor), antes tinha if ((angle[i] >= -155) && (angle[i] <= -125))
            if ((angle[i] >= 125) && (angle[i] <= 155)) { angleRaw[i] = 135; red=255; green=127; blue=0; /*useColorLine = new Scalar(255, 127, 0); */ }//Laranja
            if ((angle[i] >= 25) && (angle[i] <= 55)) { angleRaw[i] = 45; red=255; green=255; blue=0; /*useColorLine = new Scalar(255, 255, 0);*/  }//Amarelo

            if(!((red==0)&&(green==255)&&(blue==0))) //se não for verde (cor default), imprime. Isto é, somente imprime retas com ângulos identificados
                Imgproc.line(dest, new Point(l[0], l[1]), new Point(l[2], l[3]), new Scalar(blue,green,red), 1, Imgproc.LINE_AA, 0);
        }
        /*
    //Determina pontos de um bounding box básico (basiados em retas verticais)
        for (size_t i = 0; i < lines.size(); i++)
        {
            li = lines[i];
            if ((angleRaw[i] == 85) || (angle[i] == 90) || (angleRaw[i] == 95))
            {//define quadrilatero onde estão as retas próximas à 90°
                rect=crescentOrder(li[0], li[1], li[2], li[3]);//ordena o lido de uma única linha

                //escolhe o menor e maior dentre todas linhas
                if (rect.x0 <= x0) x0 = rect.x0;
                if (rect.y0 <= y0) y0 = rect.y0;
                if (rect.x1 >= x1) x1 = rect.x1;
                if (rect.y1 >= y1) y1 = rect.y1;
            }
        }*/


        //int averageI, averageJ, distance;
        //int xi0,  xi1, xj0, xj1;

        //Class Id começa com 1, no for de classificação, clusters (por proximidade "gap") de linhas com memsmo ângulo recebem o mesmo ClassId no array classifier
        int class45IdCounter = 0;
        int class85IdCounter = 0;
        int class90IdCounter = 0;
        int class95IdCounter = 0;
        int class135IdCounter = 0;
        int gap=30;//grau de proximidade das linhas para definir um cluster

        //classificação - numerar os clusters
        //for (int i = 0; i < lines.size(); i++)
        for (int i = 0; i < lines.rows(); i++)
        {
            double[] l = lines.get(i, 0);
            class45IdCounter = classCounter(45, class45IdCounter, i, gap, l, lines, angleRaw, classifier);
            class85IdCounter = classCounter(85, class85IdCounter, i, gap, l, lines, angleRaw, classifier);
            class90IdCounter = classCounter(90, class90IdCounter, i, gap, l, lines, angleRaw, classifier);
            class95IdCounter = classCounter(95, class95IdCounter, i, gap, l, lines, angleRaw, classifier);
            class135IdCounter = classCounter(135, class135IdCounter, i, gap, l, lines, angleRaw, classifier);
        }

        //Carga dos clusters baseado na classificação das linhas, o ClassIdCounter termina o for com a quantidade de clusters

        Feature[] line45 = new Feature[class45IdCounter];
        Feature[] line85 = new Feature[class85IdCounter];
        Feature[] line90 = new Feature[class90IdCounter];
        Feature[] line95 = new Feature[class95IdCounter];
        Feature[] line135 = new Feature[class135IdCounter];

       /* for(int i = 0; i<class45IdCounter; i++ ){line45[i] = new Feature();}
        for(int i = 0; i<class85IdCounter; i++ ){line85[i] = new Feature();}
        for(int i = 0; i<class90IdCounter; i++ ){line90[i] = new Feature();}
        for(int i = 0; i<class95IdCounter; i++ ){line95[i] = new Feature();}
        for(int i = 0; i<class135IdCounter; i++ ){line135[i] = new Feature();}*/



        //Log.i(TAG, "bug 00a 45 "+ class45IdCounter);
        //line45.add(new Feature());
        //line45.get(0).angleRaw = 45;
        //Log.i(TAG, "bug 00a 45 "+ line45.get(0).angleRaw);

        featureVectorCharger(dest, 45, line45, class45IdCounter, lines, angleRaw, classifier);
        featureVectorCharger(dest, 85, line85, class85IdCounter, lines, angleRaw, classifier);
        featureVectorCharger(dest, 90, line90, class90IdCounter, lines, angleRaw, classifier);
        featureVectorCharger(dest, 95, line95, class95IdCounter, lines, angleRaw, classifier);
        featureVectorCharger(dest, 135, line135, class135IdCounter, lines, angleRaw, classifier);

        //imprime retangulos nos clusters identificados
        //identifyClusters(dest, line45, class45IdCounter, new Scalar(0, 255, 255));
        //identifyClusters(dest, line85, class85IdCounter, new Scalar(255, 0, 255));
        //identifyClusters(dest, line90, class90IdCounter, new Scalar(255, 200, 255));
        //identifyClusters(dest, line95, class95IdCounter, new Scalar(0, 0, 255));
        //identifyClusters(dest, line135, class135IdCounter, new Scalar(0, 255, 0));

        //correlação entre clusters
        //versão futura, pode fazer treinamento a partir de imagens
        identifyTower(dest,80,line45, class45IdCounter, line85, class85IdCounter, line90, class90IdCounter, line95, class95IdCounter, line135, class135IdCounter);



    }

//contador de linhas no mesmo cluster (proximidade somente em x) com mesmo angulo,
//a função recebe o angulo (classAngle), o contador de linhas atual para a o angulo, posição atual analisada, gap que indica a largura do cluster, além da linha atual (lines), angleRaw, clasifier, devolve o proprio classCounter atualizado
//classCounter deve ser chamado dentro de um for deste for o classifier tera um número que agruga a linhas com mesmo ângulo classificadas com mesmo número

    private int classCounter(int classAngle, int classIdCounter, int position, int gap, double[] li, Mat lines, int[] angleRaw, int[] classifier)
    {
        double xi0, xi1, xj0, xj1;
        if (angleRaw[position] == classAngle)
        {
            if (classifier[position] == 0) //não classificado
            {
                classifier[position] = classIdCounter;
                classIdCounter++;
            }

            if (li[0] <= li[2]) { xi0 = li[0];  xi1 = li[2]; } //garante que xi0 é menor que xi1
            else { xi0 = li[2];  xi1 = li[0]; }

            for (int j = 0; j < lines.rows(); j++)
            {
                double[] lj = lines.get(j, 0);
                if (angleRaw[j] == classAngle)
                {
                    if (lj[0] <= lj[2]) { xj0 = lj[0];  xj1 = lj[2]; } //garante que xj0 é menor que xj1
                    else { xj0 = lj[2];  xj1 = lj[0]; }

                    //printf("xi0=%d\t xi1=%d \txj0=%d \t xj1=%d\n", xi0, xi1, xj0, xj1);

                    if ((classifier[j] == 0) &&
                            ((((xi0 - gap <= xj0) && (xj0 <= xi1 + gap)) && ((xi0 - gap <= xj1) && (xj1 <= xi1 + gap))) || //algum xj está entre os xi ou é igual
                                    (((xj0 - gap <= xi0) && (xi0 <= xj1 + gap)) && ((xj0 - gap <= xi1) && (xi1 <= xj1 + gap)))))   //algum xi está entre os xj ou é igual

                        classifier[j] = classifier[position];
                }
            }
            //printf("AngleRaw=%d\tClassif=%d\tx0=%d\ty0=%d\tx1=%d\ty1=%d\n", angleRaw[position], classifier[position], li[0], li[1], li[2], li[3]);
        }
        return classIdCounter;
    }

    //carga do vetor de features X0,y0,x1 e y1 são salvos ordenados, isto é x0 é menor que x1 e y0 é menor que y1
    private void featureVectorCharger(Mat img, int classAngle, Feature[] classifiedLine, int featureSize, Mat lines, int[] angleRaw, int[] classifier)
    {
        for (int c = 0; c < featureSize; c++)
        {
            classifiedLine[c] = new Feature();

            classifiedLine[c].angleRaw = classAngle;
            classifiedLine[c].classId = c;
            classifiedLine[c].numberOfLines = 0;

            classifiedLine[c].x0 = img.cols();//img.cols;//começa na direita para ser deslocado para a esquerda
            classifiedLine[c].y0 = img.rows();//img.rows;//começa embaixo para ser deslocado para cima
            classifiedLine[c].x1 = 0;//começa na esquerda para ser deslocado para a direita
            classifiedLine[c].y1 = 0;//começa a cima para ser deslocado para baixo

            for (int i = 0; i < lines.rows(); i++){
                double[] li = lines.get(i, 0);
                if (angleRaw[i] == classAngle)
                {
                    if (classifier[i] == c+1)//c está relacionado ao array de structs feature. O Array 0 contém os dados com Classifier 1. Por isso é c+1
                    {
                        classifiedLine[c].numberOfLines++;
                        TwoPoints rect = crescentOrder(li[0], li[1], li[2], li[3]);//ordena o lido de uma única linha

                        //escolhe o menor e maior dentre todas linhas
                        if (rect.x0 < classifiedLine[c].x0) classifiedLine[c].x0 = rect.x0;
                        if (rect.y0 < classifiedLine[c].y0) classifiedLine[c].y0 = rect.y0;
                        if (rect.x1 > classifiedLine[c].x1) classifiedLine[c].x1 = rect.x1;
                        if (rect.y1 > classifiedLine[c].y1) classifiedLine[c].y1 = rect.y1;
                    }
                }

            }

        }
    }

    void identifyClusters(Mat dest, Feature[] classifiedLine, int featureSize, Scalar color)
    {
        for (int c = 0; c < featureSize; c++){
            rectangle(dest, new Point(classifiedLine[c].x0, classifiedLine[c].y0), new Point(classifiedLine[c].x1, classifiedLine[c].y1), color, 2, 1);//Em java o retangulo é tratado com (x0,y0) e (x1,y1) , em C++ (x0,y0) e (width,height)
            Log.i(TAG, "identifyClusters AngleRaw=" + classifiedLine[c].angleRaw + " ClassId=" + classifiedLine[c].classId +" NumberOfLines= "+classifiedLine[c].numberOfLines);

        }
    }

    //recebe dois pontos xy e retorna ordenados
    private TwoPoints crescentOrder(double xa, double ya, double xb, double yb)
    {
        TwoPoints ordered = new TwoPoints();

        if (xa <= xb) { ordered.x0 = xa;  ordered.x1 = xb; } //garante que xi0 é menor que xi1
        else { ordered.x0 = xb;  ordered.x1 = xa; }

        if (ya <= yb) { ordered.y0 = ya;  ordered.y1 = yb; } //garante que yi0 é menor que yi1
        else { ordered.y0 = yb;  ordered.y1 = ya; }

        return ordered;
    }

    //Identifica uma torre a partir dos clusters de linhas agrupados em 45°, 85°, 90°,95° e 135°
//Sendo que essencialmente a base da torre é composta por linhas de 85° à esquerda e linhas de 95° à direita. Se tiver linhas de 45° ou 135° no meio a certerza é maior, e se tiver 45 e 135 a certeza é maior ainda

    private void identifyTower(Mat dest, int gap, Feature[] line45, int line45Size, Feature[] line85, int line85Size, Feature[] line90, int line90Size, Feature[] line95, int line95Size, Feature[] line135, int line135Size)
    {
        boolean found45inside;
        boolean found135inside;
        TwoPoints returnRect = new TwoPoints();
        Rect2d box = new Rect2d();
        //busca pelo corpo da torre (body)
        for (int c85 = 0; c85 < line85Size; c85++)
            for (int c95 = 0; c95 < line95Size; c95++)
                //if aninhado para ficar mais leve, se não passo primeiro não precisa testar os outros
                if((line85[c85].numberOfLines > 1) && (line95[c95].numberOfLines > 1)) //exclui linhas soltas
                    if(line85[c85].x0 < line95[c95].x1)  //menor X de 85° e maior x de 95°, isto é 85° à esquerda de 95° à direita e
                        if(abs(line85[c85].x1-line95[c95].x0) < gap) //a diferença menor que gap entre o maior x de 85° e o menor x de 95°
                            if((line85[c85].y0 <= line95[c95].y0) && (line95[c95].y0 <= line85[c85].y1)|| //y0 de 95° entre os ys de 85 ou
                                    (line85[c85].y0 <= line95[c95].y1) && (line95[c95].y1 <= line85[c85].y1)|| //y1 de 95° entre os ys de 85 ou
                                    (line95[c95].y0 <= line85[c85].y0) && (line85[c85].y0 <= line95[c95].y1)|| //y0 de 85° entre os ys de 95 ou
                                    (line95[c95].y0 <= line85[c85].y1) && (line85[c85].y1 <= line95[c95].y1))  //y1 de 85° entre os ys de 95
                            {
                                returnRect.x0 = line85[c85].x0;
                                returnRect.x1 = line95[c95].x1;

                                if(line85[c85].y0 <= line95[c95].y0) returnRect.y0 = line85[c85].y0;
                                else returnRect.y0 = line95[c95].y0;

                                if (line85[c85].y1 > line95[c95].y1) returnRect.y1 = line85[c85].y1;
                                else returnRect.y1 = line95[c95].y1;

                                found45inside = false;
                                for (int c45 = 0; c45 < line45Size; c45++)
                                    if( (((returnRect.x0 <= line45[c45].x0) && (line45[c45].x0 <= returnRect.x1)) ||  //x0 de 45° dentro do quadrilátero ou
                                            ((returnRect.x0 <= line45[c45].x1) && (line45[c45].x1 <= returnRect.x1))) &&  //x1 de 45° dentro do quadrilátero e
                                            (((returnRect.y0 <= line45[c45].y0) && (line45[c45].y0 <= returnRect.y1)) ||  //y0 de 45° dentro do quadrilátero ou
                                                    ((returnRect.y0 <= line45[c45].y1) && (line45[c45].y1 <= returnRect.y1))) )   //y1 de 45° dentro do quadrilátero
                                        found45inside = true;

                                found135inside = false;
                                for (int c135 = 0; c135 < line135Size; c135++)
                                    if ((((returnRect.x0 <= line135[c135].x0) && (line135[c135].x0 <= returnRect.x1)) ||  //x0 de 135° dentro do quadrilátero ou
                                            ((returnRect.x0 <= line135[c135].x1) && (line135[c135].x1 <= returnRect.x1))) &&  //x1 de 135° dentro do quadrilátero e
                                            (((returnRect.y0 <= line135[c135].y0) && (line135[c135].y0 <= returnRect.y1)) ||  //y0 de 135° dentro do quadrilátero ou
                                                    ((returnRect.y0 <= line135[c135].y1) && (line135[c135].y1 <= returnRect.y1))))   //y1 de 135° dentro do quadrilátero
                                        found135inside = true;
                                if (found45inside && found135inside)
                                {
                                    Log.i(TAG, "Tower Body");
                                    rectangle(dest, new Point(returnRect.x0, returnRect.y0), new Point(returnRect.x1, returnRect.y1), new Scalar(255, 0, 255), 2, 1);//Em java o retangulo é tratado com (x0,y0) e (x1,y1) , em C++ (x0,y0) e (width,height)
                                    putText(dest, "Tower Body", new Point(returnRect.x0, returnRect.y1 + 15), FONT_HERSHEY_SIMPLEX, 0.5f, new Scalar(255, 0, 255), 2, 8);

                                    /*box.x = returnRect.x0;
                                    box.y = returnRect.y0;
                                    box.width = returnRect.x1 - returnRect.x0;
                                    box.height = returnRect.y1 - returnRect.y0;

                                    if (BoxSavedTowerBody.x == 0 && BoxSavedTowerBody.y == 0 && BoxSavedTowerBody.width == 0 && BoxSavedTowerBody.height == 0) BoxSavedTowerBody = box;//Inicializa no primeiro frame

                                    //Filtro de impulso na corelação interframe do box
                                    BoxSavedTowerBody.x = 0.05*box.x + 0.95*BoxSavedTowerBody.x;
                                    BoxSavedTowerBody.y = 0.05*box.y + 0.95*BoxSavedTowerBody.y;
                                    BoxSavedTowerBody.width = 0.05*box.width + 0.95*BoxSavedTowerBody.width;
                                    BoxSavedTowerBody.height = 0.05*box.height + 0.95*BoxSavedTowerBody.height;

                                    Log.i(TAG, "Tower Body");
                                    //rectangle(dest, BoxSavedTowerBody, new Scalar(255, 0, 255), 2, 1);
                                    rectangle(dest, new Point(BoxSavedTowerBody.x, BoxSavedTowerBody.y), new Point(BoxSavedTowerBody.width, BoxSavedTowerBody.height), new Scalar(255, 0, 255), 2, 1);//Em java o retangulo é tratado com (x0,y0) e (x1,y1) , em C++ (x0,y0) e (width,height)
                                    putText(dest, "Tower Body", new Point((int)BoxSavedTowerBody.x, (int)BoxSavedTowerBody.y + (int)BoxSavedTowerBody.height + 15), FONT_HERSHEY_SIMPLEX, 0.5f, new Scalar(255, 0, 255), 2, 8);*/
                                }
                            }
        //busca pelo topo da torre (Top)
        //if aninhado no for para ficar mais leve, se não encontrou no primeiro não precisa testar os outros
        for (int c90 = 0; c90 < line90Size; c90++)
            if (line90[c90].numberOfLines > 10)
            {
                returnRect.x0 = line90[c90].x0;
                returnRect.y0 = line90[c90].y0;
                returnRect.x1 = line90[c90].x1;
                returnRect.y1 = line90[c90].y1;

                Log.i(TAG, "Tower Top");
                rectangle(dest,  new Point(returnRect.x0, returnRect.y0), new Point(returnRect.x1, returnRect.y1), new Scalar(0, 255, 255), 2, 1);//Em java o retangulo é tratado com (x0,y0) e (x1,y1) , em C++ (x0,y0) e (width,height)
                putText(dest, "Tower Top", new Point(returnRect.x0, returnRect.y1 + 15), FONT_HERSHEY_SIMPLEX, 0.5f, new Scalar(0, 255, 255), 2, 8);

                //	 O algoritimo está bom, mas está começando a parecer que deveria ser treinado (pesos para os clusters de retas, de forma que eu preciso de uma base, como vou precisar da base para comparar no tensorflow, vou construir e testar a base com o tensor flow para depois voltar aqui)
                /*box.x = returnRect.x0;
                box.y = returnRect.y0;
                box.width = returnRect.x1 - returnRect.x0;
                box.height = returnRect.y1 - returnRect.y0;

                if (BoxSavedTowerTop.x == 0 && BoxSavedTowerTop.y == 0 && BoxSavedTowerTop.width == 0 && BoxSavedTowerTop.height == 0) BoxSavedTowerTop = box;//Inicializa no primeiro frame

                //Filtro de impulso na corelação interframe do box																																				   //Filtro de impulso na corelação interframe do box
                BoxSavedTowerTop.x = 0.05*box.x + 0.95*BoxSavedTowerTop.x;
                BoxSavedTowerTop.y = 0.05*box.y + 0.95*BoxSavedTowerTop.y;
                BoxSavedTowerTop.width = 0.05*box.width + 0.95*BoxSavedTowerTop.width;
                BoxSavedTowerTop.height = 0.05*box.height + 0.95*BoxSavedTowerTop.height;

                Log.i(TAG, "Tower Top");
                rectangle(dest,  new Point(BoxSavedTowerBody.x, BoxSavedTowerBody.y), new Point(BoxSavedTowerBody.width, BoxSavedTowerBody.height), new Scalar(0, 255, 255), 2, 1);
                putText(dest, "Tower Top", new Point((int)BoxSavedTowerTop.x, (int)BoxSavedTowerTop.y + (int)BoxSavedTowerTop.height + 15), FONT_HERSHEY_SIMPLEX, 0.5f, new Scalar(0, 255, 255), 2, 8);*/

            }

	/*for (int c85 = 1; c85 < line85Size; c85++)
		if (line85[c85].numberOfLines > 10)
			for (int c90 = 0; c90 < line90Size; c90++)
				if (line90[c90].numberOfLines > 8)
					for (int c95 = 0; c95 < line95Size; c95++)
						if (line95[c95].numberOfLines > 8)
						{
							returnRect.x0 = line85[c85].x0;
							if(line90[c90].x0 < returnRect.x0) returnRect.x0 = line90[c90].x0;
							if(line95[c95].x0 < returnRect.x0) returnRect.x0 = line95[c95].x0;

							returnRect.y0 = line85[c85].y0;
							if (line90[c90].y0 < returnRect.y0) returnRect.y0 = line90[c90].y0;
							if (line95[c95].y0 < returnRect.y0) returnRect.y0 = line95[c95].y0;

							returnRect.x1 = line85[c85].x1;
							if (line90[c90].x1 > returnRect.x1) returnRect.x1 = line90[c90].x1;
							if (line95[c95].x1 > returnRect.x1) returnRect.x1 = line95[c95].x1;

							returnRect.y1 = line85[c85].y1;
							if (line90[c90].y1 > returnRect.y1) returnRect.y1 = line90[c90].y1;
							if (line95[c95].y1 > returnRect.y1) returnRect.y1 = line95[c95].y1;

							box.x = returnRect.x0;
							box.y = returnRect.y0;
							box.width = returnRect.x1 - returnRect.x0;
							box.height = returnRect.y1 - returnRect.y0;
							printf("Top Tower\n");
							rectangle(src, box, Scalar(0, 255, 255), 2, 1);
						}*/

        //return returnRect;//por enquanto está retornando o último retangulo, na verdade deveria ser retornado um array.
    }


}


