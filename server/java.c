/* wrapper for the GNU java library */
main(int argc,char**argv){
  extern void java_bridge_main_gcj(int argc, char**argv);

  java_bridge_main_gcj(argc,argv);
}
