# ApacheFOP.Serverless
*ApacheFOP.Serverless* is a ready to use server-less implementation of Apache FOP via Azure Functions. This provides a micro-service for dynamically rendering PDF binary outputs from XSL-FO source using Apache FOP.

You should be able to pull this code down, open with IntelliJ (after installing the Azure Toolkit for IntelliJ), and deploy to your own Subscription/Resource group and be up and running in minutes.

### Technical Summary:
This project provides a REST API that recieves a POST body containing a well formed Xsl-FO Xml document ([like these Apache FOP samples](https://github.com/apache/xmlgraphics-fop/tree/trunk/fop/examples/fo/basic)). The service will respond with the rendered Pdf binary (file bytes).

**Azure Function API Path:** `/api/apache-fop/xslfo`

**Postman Example:**
<p align="center">
<img src="/postman-test-fonts-fo.png" style="width:auto;height:auto;max-width:1200px;">
</p>
## Project Overview:
Generating high quality printable PDF outputs from a highly flexibly [pdf templating approach (separating content/data from presentation)](https://github.com/cajuncoding/PdfTemplating.XslFO) hasn't been easy in the world of .Net -- vs the world of Java where ApacheFOP has been around for a very long time.

Xsl-FO is still one of the best ways to maintain strong software development practices by rendering PDF (as a presentation output) from separate content/data + template.  And Xsl-FO offers features that some approaches just can't do (looking at you *Crystal Reports*). 

There has been a fully managed .Net C# port of [ApacheFOP](https://xmlgraphics.apache.org/fop/) based on a pre-v1.0 version (*is my guesstimate*).  But ApacheFOP is now on [v2.5 as of May 2020!](https://xmlgraphics.apache.org/fop/2.5/changes_2.5.html)

So my goal has been, for a while, to take advantage of the many great innovations in the past several years to provide an interoperable integration between Java ApacheFOP and .Net, without resorting to [something that makes my eyes cross (ugg).](http://codemesh.com/products/juggernet/).

Taking advantage of some awesome new innovations (in Azure) we can do this in a much cleaner way using:
 - **[C# .Net for Templating](https://github.com/cajuncoding/PdfTemplating.XslFO)** *(anything other than native Java will benefit from this)*
 - **Microservice** for integration architecture
 - **REST API** for interoperability
 - **Azure Function** for native Java Support
 - **[ApacheFOP](https://xmlgraphics.apache.org/fop/)** for the free & robust XslFO processing implementation

So that's the goal of this project, to provide a ready-to-deploy microservice implementation of ApacheFOP via Azure Functions. And enable others to pull the code down, deploy to their Azure subscription/resource group and be up and running in minutes -- especially those less experienced in Java.

I'm definitely not the only person to think of this, and definitely got a jumpstart from [this helpful article written by Marc Mathijssen.](https://medium.com/@marcmathijssen/using-the-power-of-apache-fop-in-a-net-world-using-azure-functions-971669b888dc)...

But, I did find that article left alot of nebulous details unclear, and would expect that a Java novice may really struggle to get it up and running. Especially the dependency on the command line tutorial, and lack of guidance on Java IDE, or how to Debug/Test, etc.

And ultimately, it didn't provide any insight on how to configure Maven correctly *(I can hear some devs asking "what's Maven" right now)* or any code. So, **Kudos** for the intro, but I hope this helps to bring it across the goal line!

## Getting Started:
Here's the high level steps to get started...

1. First start with this article to get your Java IDE *IntelliJ IDEA* setup with Azure Toolkit!
   - [https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij)
   - It uses [IntelliJ IDEA](https://www.jetbrains.com/idea/); the best Java IDE available (*in my humble opinion*), especially for those new to Java :-)
   - It guides you on the use of **The Azure Toolkit** for IntelliJ IDEA which will make debugging & deploying a breeze...
2. This project has already been configured using an Azure Functions Maven archetype, and all necessary dependencies for ApacheFOP, Apache Commons libraries, etc. have already been configured correctly.
3. Once IntelliJ is up and running, you can pull down the Repo, and then just open the `apachefop-serverless-az-func` as the root project -- I just right click and select _**"Open Folder as IntelliJ Community Edition Project"**_.
4. It's usually a good idea to reload the Maven pom.xml -- which will refresh and ensure that all dependencies are ready to go.
5. [Run the function app locally and debug](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij#debug-the-function-app-locally) . . . click the good-ole Debug Icon in IntelliJ and fire up the micro-service locally.
   - Yes, this will fully support local execution, testing, and debugging!
6. Install Postman (or equivalent) and play with it...
7. Finally, [Deploy to Azure using the Azure Toolkit](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-maven-intellij#deploy-your-function-app-to-azure) whenever you're ready...

## Additional Features:

#### GZIP Compression:
In addition to rendering the PDF, this service already implements GZIP compression for returning the Binary PDF files.  This can greatly improve performance and download times, especially for PDFs rendered without much imagery, as the text content bytes tends to be more compressible.

All you need to do is add the `Accept-Encoding` header with a value of `gzip` to the request:
```
Accept-Encoding=gzip
```
<p align="center">
    <img src="/postman-header-enable-gzip.png" style="width:auto;height:auto;max-width:800px;">
</p>

## Calling the Service from .Net

### Snippet:
Because I talked about follow-trough up above, I'd be amiss if I didn't provide a sample implementation of calling this code from .Net.

Assuming the use of the great *RESTSharp library* for REST api calls, and the Xsl-FO content is validated and parsed as an *XDocument* (Linq2Xml).

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

It illustrates the use of both Xslt & Razor templates from ASP.Net MVC to render PDF Binary reports dynamically from queries to the [Open Movie Database API](http://www.omdbapi.com/).  And has been enhanced to also now illustrate the use of ApacehFOP.Serverless micro-service for rendering instead of the embedded legacy FO.Net implementation.

The following page url's will render the dynamic Pdf using ApacheFOP.Serverless.
 
 *NOTE: You will need to have ApacheFOP.Serverless project running either locally or in your own instance of Azure :-) Just update the Web.config to point to your Host (Local or Azure).*

 - http://localhost:57122/movies/pdf/razor/apache-fop?title=Star%20Wars
 - http://localhost:57122/movies/pdf/xslt/apache-fop?title=Star%20Wars

<p align="center">
    <img src="/pdf-templating-apache-fop-serverless-chrome-test.png" style="width:auto;height:auto;max-width:1200px;">
</p>


## Additional Background:
For many-many years, I've implemented Pdf Report generation in using [Templating approaches](https://github.com/cajuncoding/PdfTemplating.XslFO) for various clients (enterprises & small businesses) to help them automate their paper processes with dynamic generation of printable outputs such as: Pdf files, invoices, shipping/packaging labels, newletters, etc.

And, for a long while now I've known that the current C# implementation FO.Net was severly limited by the fact that it was created circa 2008 and is now [an archived CodePlex project](https://archive.codeplex.com/?p=fonet).

At one client the technology stack was fully Java based, so the use of ApacheFOP was a no-brainer; [ApacheFOP](https://xmlgraphics.apache.org/fop/) is a supported, open-source, full implementation of XslFO in Java, that has had regular updates/enhancements over the years. 

The [FO.Net](https://archive.codeplex.com/?p=fonet) C# variant was ported from Apache FOP likely from a pre-v1.0
version of ApacheFOP, but to be honest it has worked incredibly well, and reliably. As a fully managed C# solution, it ran in web projects as well a WinForms projects where viewing the rendered PDF live int he app real-time provided and wonderful user experience for my a couple projects. 

But, as things have evolved the advent of cloud services has opened doors for accomplishing this in a much more powerful/scaleable/manageable way -- particularly Azure Functions and their excellent support for varios technology languages (.Net, Java, NodeJS, etc.)!

So I finally had the time to flush out the details, and share this project. I truly hope that it helps many others out!

Now Geaux Code!
