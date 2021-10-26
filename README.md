# ApacheFOP.Serverless
*ApacheFOP.Serverless* is a ready to use server-less implementation of Apache FOP via Azure Functions. This provides a micro-service for dynamically rendering PDF binary outputs from XSL-FO source using Apache FOP.

You should be able to pull this code down, open with IntelliJ (after installing the Azure Toolkit for IntelliJ), and deploy to your own Subscription/Resource group and be up and running in minutes.

If you like this project and/or use it the please give me a Star (c'mon it's free, and it'll make my day)! 

### [Buy me a Coffee ☕](https://www.buymeacoffee.com/cajuncoding)
*I'm happy to share with the community, but if you find this useful (e.g for professional use), and are so inclinded,
then I do love-me-some-coffee!*

<a href="https://www.buymeacoffee.com/cajuncoding" target="_blank">
<img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="41" width="174">
</a> 

## Updates / Change Log
##### Updated the project to v1.4 with the following:
 - **Added support for running, debugging, and deploying from within VS Code as well as IntelliJ IDEA**.
   - Both project types use folder context configuration, so all configuration files have now been included and checked into the Repository.
   - This should make it easier to get up and running quickly with either IDE.
 - Resolved a bug in the Font loading/path handling when running in Windows Host (due to existing font paths).
 - Updated Microsoft's `azure-functions-maven-plugin` to address various issues (esp. the need for a GUID in the deployment name which broke VS Code's ability to debug).
 - Pom.xml cleanup to eliminate various "*Problems*" flagged by VS Code's pom parsing (using M2Eclipse processor
 - Various small code cleanup items as noted in VS Code Java "*Problems*" tab.
 
##### Updated the project to v1.3 with the following:
 - Added support for Azure Function configuration capability to enable Accessibility since Apache FOP `<accessibility>` xml config element is not working as of v2.6.
   - Added an XslFO markup sample to test/demonstrate Accessibility in `resources/samples/WorkinWithAccessibilitySample.fo`.
   - Updated KeepinItWarm.fo to run correctly when Accessibility is enabled.
 - Added in-memory caching of Java embedded resources that are resolved (e.g. Fonts) for performance.
 - Code cleanup.

##### Updated the project to v1.2 with the following:
 - Added support for Custom Font integration as Resource Files in the project and deployed with the JAR!
   - This enables adding fonts easily by simply dropping them in the `resources/fonts` folder, and then registering them via configuration in the `apache-fop-config.xml` according to [Apache FOP Font Documentation](https://xmlgraphics.apache.org/fop/2.6/fonts.html#register).
   - Added a a couple sample (free) custom fonts and sample markup `resources/samples/WorkinWithFontsSample.fo` in the project.
 - Fixed bug in the render Event Log debug details returned in the Http Header whereby Apache FOP may send Unicode Characters but only ASCII are allowed; Unicode are now safely escaped into their respective Hex Codes so that the message is readable.
 - Fixed issue in Maven build to enforce the clean stage so the artifact always contains the latest changes (e.g. especially physical resource file changes) when debugging.
 - Some miscellaneous code cleanup.

##### Updated the project to v1.1 as it now incorporates:
 - Upgraded the project to use Java v11 now as the latest long term supported (LTS) version for Azure Functions (aka Zulu Java v11)
   - _Previously was Java 8 (v1.8) (aka Zulu Java v8)._
 - Bumping the versions of all dependencies to the latest stable versions
 - Bumping the Apache FOP version to v2.6 (just released in Jan 2021)
 - Adding support for configuration Xml to fully configure ApacheFOP Factory by editing the `/src/main/resources/apache-fop-config.xml' as needed.
   - _The configuration will be bundled and deployed with the application._
 - Now includes an existing `apache-fop-config.xml` file which enables Font 'auto-detect' feature for much better Font support.
 - Removed dependency on `com.sun.deploy.net.HttpRequest` import as importing it no longer compiles on the latest versions of IntelliJ IDEA; little value was added by using only one constant that was needed: _ACCEPT_ENCODING_
 - All Heading and Content type constants are now self-contained so no additional dependencies are needed.
   - _This enabled removal of the dependency on com.sun.deploy.net.HttpRequest import as importing it no longer compiles on the latest versions of IntelliJ IDEA, and is a bad practice.  Little value was added by using only 1 constant was needed, ACCEPT_ENCODING_

 - Notable cleanup & optimization of the Pom.xml
 - Implemented a fix for a possilbe deployment risk when AppName and ResourceGroupName values are not unique with the azure-functions-maven-plugin
   - _As noted here: https://github.com/Azure/azure-functions-java-worker/issues/140_


## Technical Summary:
This project provides a REST API that recieves a POST body containing a well formed Xsl-FO Xml document ([like these Apache FOP samples](https://github.com/apache/xmlgraphics-fop/tree/trunk/fop/examples/fo/basic)). The service will respond with the rendered Pdf binary (file bytes).

If an error occurs -- likely due to incorrect Xsl-FO syntax or structure -- then an Http 500 Response will be returned with a JSON payload containing details about the error.  For issues with the Xsl-FO parsing/processing, ApacheFOP generally provides very helpful info. about the line, location, and markup that caused the error so that it can be resolved.

#### Azure Function API:
 - Path: **`/api/apache-fop/xslfo`**
 - Request Type: **POST**
 - Request Body: **Xsl-FO Content as valid Xml** to Render

#### Responses:
 - **Http 200-OK**: *Binary File paylaod containing the rendered Pdf File*
 - **Http 500-InternalServerError** -- *Json response payload with ApacheFOP processing error details.*


#### Postman Example:
<p align="center">
<img src="/postman-test-fonts-fo.png" style="width:auto;height:auto;max-width:1200px;">
</p>

## Project Overview:
Generating high quality printable PDF outputs from a highly flexible [pdf templating approach (separating content/data from presentation)](https://github.com/cajuncoding/PdfTemplating.XslFO) hasn't been easy in the world of .Net -- vs the world of Java where ApacheFOP has been around for a very long time.

For a more exhaustive dive into why PDF templating and markup based solutions are more powerful than report designer based solutions -- in today's modern web apps -- 
I ramble on about that over here in: 
 - [Part 1: Considerations for a robust PDF or Web reporting solution?](https://cajuncoding.com/2020-11-17-pdf-reports-part1-how-hard-can-it-be/)
 - [Part 2: Why markup based PDF Templating is the way to go...](https://cajuncoding.com/2020-11-18-pdf-reports-part2-ok-where-does-that-leave-us/)

Suffice it to say that markup based solutions have alot of value, and Xsl-FO is still one of the best ways to maintain strong software development practices by rendering PDF outputs (as a presentation output) from separated content/data + template. And Xsl-FO offers features that some approaches just can't do (looking at you *Crystal Reports*).

There has been a fully managed .Net C# port of [Apache FOP](https://xmlgraphics.apache.org/fop/) (FO.Net) based on a pre-v1.0 version (*is my guesstimate*); it's old & unsupported, but still fairly functional, and I've used it very successfully on several projects. But Apache FOP is now on [v2.5 as of May 2020!](https://xmlgraphics.apache.org/fop/2.5/changes_2.5.html) with annual/bi-annual support updates still being released.

So my goal has been, for a while, to take advantage of the many great innovations in the past several years to provide an interoperable integration between Java Apache FOP and .Net, without resorting to [something that makes my eyes cross (ugg).](http://codemesh.com/products/juggernet/).

Taking advantage of some awesome new innovations (in Azure) we can do this in a much cleaner way using:
 - **[C# .Net for Templating using Razor](https://github.com/cajuncoding/PdfTemplating.XslFO)** *(anything other than native Java will benefit from this)*
 - **Microservice** for integration architecture
 - **REST API** for interoperability
 - **Azure Function** for native Java Support
 - **[Apache FOP](https://xmlgraphics.apache.org/fop/)** for the free & robust XSL-FO processing implementation

So that's the goal of this project, to provide a ready-to-deploy microservice implementation of Apache FOP via Azure Functions. And enable others to pull the code down, deploy to their Azure subscription/resource group and be up and running in minutes -- especially those less experienced in Java.

I'm definitely not the only person to think of this, and definitely gleaned some insight from [this article written by Marc Mathijssen.](https://medium.com/@marcmathijssen/using-the-power-of-apache-fop-in-a-net-world-using-azure-functions-971669b888dc)...

But, I did find that article left alot of nebulous details unclear, and would expect that a Java novice may really struggle to get it up and running. Especially the dependency on the command line tutorial, and lack of guidance on Java IDE, or how to Debug/Test, etc.

And ultimately, it didn't provide any insight on how to configure Maven correctly *(I can hear some devs asking "what's Maven" right now)* or any code at all really. So, **Kudos** for the intro, but I hope this helps to bring it across the goal line!

## Getting Started:
Here's the high level steps to get started...

#### Want to run it locally?
1. First get your Java IDE (_**IntelliJ IDEA**_ or _**VS Code**_) setup with Azure Toolkit!
   - **IntelliJ IDEA:**
     - Setup IntelliJ for Azure Function development:[https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij)
       - NOTE: As of June 30th, 2021, the original Zulu JDK is now deprecated and Microsoft's OpenJDK should now be installed instead.
       - Install the Azure Functions supported JDK from **Microsoft OpenJDK v11**: [https://docs.microsoft.com/en-us/azure/developer/java/fundamentals/java-support-on-azure](https://docs.microsoft.com/en-us/azure/developer/java/fundamentals/java-support-on-azure)
     - [IntelliJ IDEA](https://www.jetbrains.com/idea/), my humble opinion is the best Java IDE available (*in my humble opinion*), especially for those new to Java :-)
     - this guide will walk you through use of **The Azure Toolkit** for IntelliJ IDEA which will make debugging & deploying a breeze...
   - **VS Code:**
     - Install the Java Extensions in VS Code: [https://code.visualstudio.com/docs/java/java-tutorial#_installing-extensions](https://code.visualstudio.com/docs/java/java-tutorial#_installing-extensions)
     - Install the Azure Functions supported JDK from **Microsoft's OpenJDK v11**: 
       - Using the VS Code Runtime Configuration Wizard (recommended): [https://code.visualstudio.com/docs/java/java-tutorial#_using-the-java-runtime-configuration-wizard](https://code.visualstudio.com/docs/java/java-tutorial#_using-the-java-runtime-configuration-wizard)
       - Or manually, and then configuring it within VS Code: [https://docs.microsoft.com/en-us/azure/developer/java/fundamentals/java-support-on-azure](https://docs.microsoft.com/en-us/azure/developer/java/fundamentals/java-support-on-azure)
2. This project has already been configured using an Azure Functions Maven archetype, and all necessary dependencies for ApacheFOP, Apache Commons libraries, etc. have already been configured correctly.
3. Once IntelliJ or VS Code is up and running, you can pull down the Repo, and then just open the `apachefop-serverless-az-func` as the root project -- I just right click and select:
   - IntelliJ: _**"Open Folder as IntelliJ Community Edition Project"**_.
   - VS Code: _**"Open with Code"**_
4. It's usually a good idea to reload the Maven pom.xml and kick off a build to load all dependencies by running/executing the **`package`** phase in the Maven console of either IntelliJ or VS Code.
5. IntelliJ: Run the function app locally and debug via [IntelliJ](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij#debug-the-function-app-locally) or [VS Code](https://code.visualstudio.com/docs/java/java-tutorial#_running-and-debugging-your-program) . . . click the good-ole Debug/Run Icon and fire up the micro-service locally.
   - Yes, this will fully support local execution, testing, and debugging!
6. Install Postman/Insomnia/etc. and play with it by posting your Xsl-FO Markup to the Service and seeing your PDF be returned (see above screenshots)...
7. Finally, Deploy to Azure using the Azure Toolkit via [IntelliJ](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij#deploy-your-function-app-to-azure) or [VS Code](https://docs.microsoft.com/en-us/azure/developer/javascript/tutorial/vscode-function-app-http-trigger/tutorial-vscode-serverless-node-deploy-hosting) whenever you're ready...

#### Just want it Running in Azure and don't want to bother with any local installations?
If you'd rather just deploy directly to Azure, then there's some info on using Github Actions to do just that with no local installation required shared over here: https://github.com/cajuncoding/ApacheFOP.Serverless/issues/3#issuecomment-950820278 

## Additional Features:

### GZIP Compression:
In addition to rendering the PDF, this service already implements GZIP compression for returning the Binary PDF files. This can greatly improve performance and download times, especially for PDFs rendered without much imagery, as the text content bytes tends to be more compressible.

All you need to do is add the `Accept-Encoding` header with a value of `gzip` to the request:
```
Accept-Encoding=gzip
```
<p align="center">
    <img src="/postman-header-enable-gzip.png" style="width:auto;height:auto;max-width:800px;">
</p>

### Custom Fonts via Resource Files:
##### Add Font Files (*.ttf, *.otf, etc.)
To easily utilize custom fonts with the Azure Functions deployment, this project provides the ability to simply add them into the project as resource
files by simply placing them in the `src/main/resources/fonts` folder.  So you can literally just copy them into the project and deploy. In IntelliJ IDEA the structure will look like:
<p align="center">
    <img src="/intellij-resources-fonts-project-structure.png" style="width:auto;height:auto;max-width:500px;">
</p>

Once there they can be resolved at runtime by the application even after being deployed to Azure Functions; because they will be embedded resources with the JAR file. ApacheFOP.Serverless has a custom `ResourceResolver` implementation that can then locate these via relative path is used when registering the fonts via configuration in the `apache-fop-config.xml` according to [Apache FOP Font Documentation](https://xmlgraphics.apache.org/fop/2.6/fonts.html#register).

### Enable Accessibility:
Apache FOP Supports accessibility compliance in PDFs however, the `<accessibility>` xml configuration attribute noted in the documentation ([here](https://xmlgraphics.apache.org/fop/2.6/accessibility.html)) does not work as of v2.6. 

Therefore ApacheFOP.Serverless provides an Azure Function configuration value to set this directly which can be enabled by setting the Azure Functions environment config value: `'AccessibilityEnabled' = 'true'`.

## Basic Azure Functions Configuration Values:
Ensure that the configuration values are set correctly for your Azure Function...  
- When deployed, you will set these as variables in the Portal `Settings -> Configuration` of the Azure Function.  
- When running locally in IntelliJ these will be set in the AppSettings of the Run/Debug Configuration for IntelliJ.  And, in VS Code these are stored in the `local.settings.json` configuration file (included).

Configuration Values:
- `FUNCTIONS_WORKER_RUNTIME` = `java`
- `FUNCTIONS_EXTENSION_VERSION` = `~3`
- `FUNCTIONS_CORE_TOOLS_DISPLAY_LOGO` = `true` (pretty sure this is optional, but I like having it)
- `JAVA_HOME` = `C:\Program Files\Zulu\zulu-11\` _(may be optional, but at one point this was the only way I got it to find the right version before uninstalling everything else)_
- `AzureWebJobsStorage` = `UseDevelopmentStorage=true` OR `Initialize a new Azure Function in the Portal to get a valid Web Jobs storage key` 
  - _Note: this one is required for the **KeepWarmFunction** that helps keep the Azure function performance up after sitting for a while...  you can always just comment out that Function class to eliminate this dependency, but it's very helpful in production environments.)_
  - [Here are the instructions for installing/running the Storage Emulator (Windows) or Azurite Emulator (Linux)](https://docs.microsoft.com/en-us/azure/storage/common/storage-use-emulator#get-the-storage-emulator) to run completely locally.
  - OR you may create your Azure function and configure your real connection string here.
- `KeepWarmCronSchedule` = `0 */5 * * * *` _(also required configuration for the KeepWarmFunction)_
- `DebuggingEnabled` = `true` (Optional but very helpful once you start using it to return debug details in the responses).

## Calling the Service from .Net

### Snippet:
Because I talked about follow-through up above, I'd be amiss if I didn't provide a sample implementation of calling this code from .Net.

Assuming the use of the great *RESTSharp library* for REST api calls, and the Xsl-FO content is validated and parsed as an *XDocument* (Linq2Xml)... this sample should get you started on the .Net side as a client calleing the new PDF microservice.

*NOTE: Just use RESTSharp and avoid [incorrectly implementing HttpClient (hint, it should be a singleton)](https://aspnetmonsters.com/2016/08/2016-08-27-httpclientwrong/)*

Snippet taken from the [implementation here](https://github.com/cajuncoding/PdfTemplating.XslFO/blob/feature/iniial_support_for_apache_fop_serverless_rendering/PdfTemplating.XslFO.Render.ApacheFOP.Serverless/XslFOPdfRenderService.cs), in my PdfTemplating project.

```csharp
using RestSharp;
using RestSharp.CustomExtensions;
using System;
using System.CustomExtensions;
using System.Threading.Tasks;
using System.Xml.Linq;

namespace PdfTemplating.XslFO.ApacheFOP.Serverless
{
    //READ from Configuration, or DI Constructor Injection
    private string apacheFOPServiceHost = "http://localhost:7071";
    private string apacheFOPServiceApi = "api/apache-fop/xslfo";

    public static class ApacheFOPServerless
    {
        public async Task<byte[]> RenderPdfBytesAsync(XDocument xslFODoc)
        {
            //Initialize the Xsl-FO microservice via configuration...
            var restClient = new RestClient(apacheFOPServiceHost);

            //Get the Raw Xml Source for our Xsl-FO to be tansformed into Pdf binary...
            var xslFoSource = xslFODoc.ToString();

            //Create the REST request for the Apache FOP micro-service...
            var restRequest = new RestRequest(apacheFOPServiceApi, Method.POST);
            restRequest.AddRawTextBody(xslFoSource, ContentType.Xml);

            //Execute the request to the service, validate, and retrieve the Raw Binary resposne...
            var restResponse = await restClient.ExecuteWithExceptionHandlingAsync(restRequest);

            var pdfBytes = restResponse.RawBytes;
            return pdfBytes;            
        }
    }
}
```

### .Net PdfTemplating (Full blown) Implementation:
A full blown implementation of templating + ApacheFOP.Serverless is in a branch of my [Pdf Templating project here](https://github.com/cajuncoding/PdfTemplating.XslFO/tree/feature/iniial_support_for_apache_fop_serverless_rendering).

It illustrates the use of both Xslt and/or Razor templates from ASP.Net MVC to render PDF Binary reports dynamically from queries to the [Open Movie Database API](http://www.omdbapi.com/). And it has now been enhanced to also illustrate the use of _ApacehFOP.Serverless_ microservice for rendering instead of the embedded legacy FO.Net implementation.

With the running application provided in the project above, the following page url's will render the dynamic Pdf using ApacheFOP.Serverless.
 
 *NOTE: You will need to have ApacheFOP.Serverless project running either locally or in your own instance of Azure :-) Just update the Web.config to point to your Host (Local or Azure).*

 - http://localhost:57122/movies/pdf/razor/apache-fop?title=Star%20Wars
 - http://localhost:57122/movies/pdf/xslt/apache-fop?title=Star%20Wars

<p align="center">
    <img src="/pdf-templating-apache-fop-serverless-chrome-test.png" style="width:auto;height:auto;max-width:1200px;">
</p>


## Additional Background:
For many-many years, I've implemented Pdf Reporting solutions with [templating approaches](https://github.com/cajuncoding/PdfTemplating.XslFO) for various clients (enterprises & small businesses) to help them automate their paper processes with dynamic generation of _printable media_ outputs such as: PDF files, invoices, shipping/packaging labels, newletters, etc.

And, for a long while now I've known that the current C# implementation FO.Net was limited by the fact that it was created circa 2008 and is now [an archived CodePlex project](https://archive.codeplex.com/?p=fonet).

At one client the technology stack was fully Java based, so the use of _Apache FOP_ was a no-brainer; [ApacheFOP](https://xmlgraphics.apache.org/fop/) is a supported, open-source, full implementation of an XSL-FO processor in Java, that has had regular updates/enhancements over the years. 

The [FO.Net](https://archive.codeplex.com/?p=fonet) C# variant was ported from Apache FOP; likely from a pre-v1.0 version of ApacheFOP, but to be 
honest it has worked incredibly well, and reliably. As a fully managed C# solution, it ran in web projects as well a WinForms projects where viewing 
the rendered PDF live int the app real-time provided and wonderful user experience for a couple of projects. 

But, as things have evolved the advent of cloud services has opened doors for accomplishing this in a much more powerful/scaleable/manageable way -- particularly Azure Functions and their excellent support for varios technology languages including: .Net, Java, NodeJS, etc.!

So I finally had the time to flush out the details, and share this project. I truly hope that it helps many others out!

Now Geaux Code!
