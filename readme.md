# Three Sixty
*A WebGL-based panoramic image viewer (written in TeaVM/Java transpiled to html/js)*

## Setup

Requires **Maven, Java 1.8** and depends on **TeaVM 6.1.0**

Recommended **parcel.js**


1. clone this repository: `$ git clone git@github.com:covicon/threesixty.git`
2. build and package it via maven: `$ mvn clean package`
3. bundle it with parcel.js: `$ parcel target/web/html/*` 
4. open it in your browser: `http:://localhost:1234/index.html?<panoramic image url>` (try it with `texture-large.jpg`)




## Development

### Maven-integrated transpilation via TeaVM 

ThreeSixty has two source code sections: `src/java` and `src/html`. 

- The java classes are compiled regularly to `target/classes`.
- All html contents is copied to `target/web/html`. (With maven property interpolation enabled for .html files)
- The TeaVM transpilation is configured to execute with the *compile goal* of the *class-generation phase*. 
  The transpilation output is put to `target/web/js`.
- Additionally the generated _.js_ file is patched to auto-execute it's entry point main method. (required for bundling)
- the bundler is started via `parcel target/web/html/*`. It bundles and serves all html/js/resource files to 
  http://localhost:1234 and updates automatically, upon change.

### TeaVM integration in your IDE

#### Eclipse

The *pom.xml* contains a *pluginsManagement* section where the org.eclipse.m2e lifecycle-management is configured. 
This will automatically configure eclipse to auto-trigger TeaVM transpilation and artifact post-processing upon full and incremental builds.


#### IntelliJ

Upon importing, the maven tool section allows plugin goals to be linked with build and rebuilding.

Register lifecycle `package` to be executed after *Build* and *Rebuild*. 