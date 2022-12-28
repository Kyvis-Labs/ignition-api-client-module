# API Client YAML Documentation

The API Client Module uses the [YAML](https://yaml.org/) syntax for configuration. Each API is represented by YAML configuration that specifies how to interface with the 3rd party API. The configuration defines the endpoints, authentication method, interactions with Ignition, and more. YAML might take a while to get used to but is really powerful in allowing you to express complex configurations.

The basics of YAML syntax are block collections and mappings containing key-value pairs. Each item in a collection starts with a  `-`  while mappings have the format `key: value`. This is somewhat similar to a Hash table or more specifically a dictionary in Python. These can be nested as well.  **Beware that if you specify duplicate keys, the last value for a key is used**.

Note that indentation is an important part of specifying relationships using YAML. Things that are indented are nested “inside” things that are one level higher. Getting the right indentation can be tricky if you’re not using an editor with a fixed width font. Tabs are not allowed to be used for indentation. Convention is to use 2 spaces for each level of indentation.

You can use the online service  [YAMLLint](http://www.yamllint.com/)  to check if your YAML syntax is correct before loading it into the module which will save you some time. If you do so, be aware that this is a third-party service and is not maintained by the API Client Module.

> **Note:** Please pay attention to not storing private data (passwords, API keys, etc.) directly in your YAML configuration. Private data is stored through variables. Variables are stored inside of Ignition's internal database with encryption.

Strings of text following a  `#`  are comments and are ignored by the system.

# Configuration Parameters

There are 7 top level configuration parameters that define an API: 

- [api](#api)
- [httpsVerification](#httpsverification)
- [variables](#variables)
- [authType](#authtype)
- [headers](#headers)
- [webhooks](#webhooks)
- [functions](#functions)

## api

The api section describes the API, version, and author.

### Parameters

**name** string *(required)*
The public name of the API.
___
**description** string *(required)*
The public description of the API.
___
**version** string *(required)*
The version of the YAML configuration.
___
**author** [author](#author) *(required)*
Describes the author of the YAML configuration.

### Example

```yaml
api:
  name: MyAPI
  description: Interfaces with MyAPI 
  version: 1.0.0
  author: 
    name: John Doe
    email: john@acme.com
    url: https://github.com/JohnDoe
```

## author

Describes the author of the YAML configuration.

### Parameters

**name** string *(required)*
The author's name.
___
**email** string *(optional)*
The author's email.
___
**url** string *(optional)*
The URL to the author's page.

## httpsVerification

Some https sites do not have a trusted HTTP certificate resulting in an exception with the request. You can disable HTTPS certificate verification by setting this parameter to false.

### Parameters

**httpsVerification** boolean *(optional)*
Whether or not to disable HTTPS verification

### Example

```yaml
httpsVerification: false
```

## variables

The variables section allows you to define any number of variables, defined as a named list, that can be used throughout the API. This avoids having to duplicate information on multiple endpoints and allows you to store sensitive data, such as passwords, without having to specify it in the YAML configuration. All variables are stored inside of Ignition's internal database with encryption and are persistent. Variables can have a static value or require the user to set the value in the configuration section of Ignition's Gateway webpage. The API can't start until all required variables are set.

The variables section is optional. Leaving the section out results in no variables defined.

### Variable Substitution

Variables can be substituted in most parameters throughout the configuration. The parameter below will indicate if variables can be used. You can substitute variables by defining the variable you want using the syntax {{var::*variable*}}. For example, if you have a variable called *baseUrl* you can use it when defining the URL of the endpoint:

```yaml
url: {{var::baseUrl}}/api/auth/login
```

Variables can also come from other variable stores, such as variables stored from other functions or tag write handlers. In those cases, you specify the variable store using a dot notation. For example, you can grab a variable from a function that is a dependency like this:

```yaml
url: {{var::baseUrl}}/person/{{var::personInfo.id}}
```

In the above example, *personInfo* is another function that gets called first before *person* and stores a variable called *id*, which is the person's identifier. 

There are 2 special variable stores:

 - webhook - Used with webhooks and stores the id, name, and url of the webhook.
 - handler - Used with tag write handlers and invoking a function through scripting. You can define any variables in the store that you want.

You can use as many variable substitutions that you want in the parameter.

### Parameters

***variableName*** [variable](#variable) *(required)*
You can provide multiple variables. Each variable is unique by the name you provide. Replace *variableName* with the name of your variable. See example below that defines 1 variable called *baseUrl*.

### Example

```yaml
variables:
  baseUrl:
    default: https://api.rach.io/1/public
    hidden: true
```

## variable

Defines a variable to use throughout your API.

### Parameters

**required** boolean *(optional)*
Whether or not the variable is required to be set before the API can start.
___
**sensitive** boolean *(optional)*
Whether or not the variable is sensitive so it is not visible to the user. All variables are stored with encryption. However, variables that are not sensitive will be shown in the configuration, such as a URL.
___
**hidden** boolean *(optional)*
Whether or not the variable is allowed to be set. If true, the editor in the configuration will be disabled.
___
**default** string *(optional)*
The default value for the variable. Useful for defining an initial value or static values.

## authType

The authType section allows you to define how to authenticate to the API. The module supports the following authentication types:

- None - No Authentication
- Bearer Token - Specify a bearer token.
- Basic Auth - Specify a username and password.
- Session Auth - Specify a username and password using a session. Session maintains cookies, basic auth and maybe other http context for you, useful when need login or other situations.
- OAuth2 - Standard OAuth2 authentication flow.

The module assumes no authentication is required if the section not defined.

### Parameters

***type*** string *(required)*
The type of authentication to use. Possible values are:

- [none](#none)
- [bearer](#bearer)
- [basic](#basic)
- [session](#session)
- [oauth2](#oauth2)

Each type has its own set of parameters. See the types below for more details.

### Example

```yaml
authType: 
  type: session
  url: {{var::baseUrl}}/api/auth/login
  params:
    - name: remember
      value: true
```
  
## none

Specifies that the API requires no authentication. Default when authType is not defined in the YAML.

### Parameters

*None*

## bearer

Specifies that the API uses bearer tokens. Automatically adds the following variables that need to be set by the user:

 - `authType-bearer-token` 
 
### Parameters

*None*

## basic

Specifies that the API uses basic HTTP authentication. Automatically adds the following variables that need to be set by the user:

 - `authType-basic-username` 
 - `authType-basic-password` 

### Parameters

*None*

## session

Specifies that the API uses session based authentication. Session will post the username and password as JSON data to an endpoint and keep track of a session. Automatically adds the following variables that need to be set by the user:

 - `authType-session-username` 
 - `authType-session-password` 

### Parameters

**url** string *(required)* *(variable substitution)*
The URL to post the authentication data.
___
**params** list of [param](#param) *(optional)*
An array of parameters to include in the JSON body along with the username and password.

## param

Represents a parameter (key value pair) used as either URL parameters or body parameters.

### Parameters

**name** string *(required)*
The parameter name.
___
**value** string *(required)* *(variable substitution)*
The parameter's value.

## oauth2

Specifies that the API uses OAuth2 for authentication and authorization. The OAuth2 flow looks like this:

 - Obtain OAuth 2.0 credentials from your API. You need to specify the client id and secret as variables.
 - Obtain an authorization code your API's authorization server. You need to specify the authorization URL as a parameter in this section. Once you authenticate on the API side, you will be redirected back to Ignition with the authorization code. You need the code to obtain an access token.
 - Obtain an access token from your API's authorization server. You need to specify the access token URL as a parameter in this section.
 - Use the access token to subsequent API calls.
 - Refresh the access token after expiration, if necessary.

OAuth2 automatically adds the following variables that need to be set by the user:

 - `authType-oauth2-client-id` 
 - `authType-oauth2-client-secret`

Additionally, OAuth2 adds the following read-only variables for reference:

 - `authType-oauth2-auth-code` 
 - `authType-oauth2-access-token`
 - `authType-oauth2-token-type`
 - `authType-oauth2-expiration`
 - `authType-oauth2-refresh-token`

### Parameters

**authUrl** string *(required)* *(variable substitution)*
The authorization URL for your API to obtain the authorization code.
___
**scope** string *(required)*
The scope for the API. Sent in the authorization URL. See your API for more details.
___
**accessTokenUrl** string *(required)* *(variable substitution)*
The access token URL for your API to obtain the access token.
___
**grantType** string *(optional)*
The OAuth2 grant type. Possible grant types are:

 - authorizationCode
 - clientCredentials

Defaults to *authorizationCode*.

## headers

The headers section allows you to define HTTP headers for all endpoints globally. For example, all endpoints may require a specific content type that you can define globally versus every single endpoint. This is represented as an array of header key value pairs.

### Parameters

Array of [header](#header)

### Example

```yaml
headers:
  - key: Content-Type
    value: application/json
```

## header

Represents a header (key value pair) used as HTTP headers.

### Parameters

**key** string *(required)*
The header name.
___
**value** string *(required)* *(variable substitution)*
The header's value.

## webhooks

The webhooks section allows you to define webhooks or custom callbacks in Ignition. Webhooks provide a way for an app to provide Ignition with real-time information using a push mechanism without having to poll for data, resulting in efficient transfer of data. Webhooks add servlets to Ignition that receive the callback. Webhooks only accept HTTP post data.

The webhooks section is optional. Leaving the section out results in no webhooks defined.

### Parameters

***webhookName*** [webhook](#webhook) *(required)*
You can provide multiple webhooks. Each webhook is unique by the name you provide. Replace *webhookName* with the name of your webhook. See example below that defines 1 webhook called *device*.

### Example

```yaml
webhooks:
  device:
    check:
      url: {{var::baseUrl}}/notification/webhook/{{var::webhook.id}}
      method: get
    add:
      depends: person
      url: {{var::baseUrl}}/notification/webhook
      method: post
      body:
        type: json
        value: |
          {"device":
            {"id":"{{var::person.deviceId}}"}, 
            "url":"{{var::webhook.url}}",
            "eventTypes":[{"id":"5"}]
          }
      actions:
        - action: variable
          name: id
          jsonPath: $.id
    remove:
      depends: person
      url: {{var::baseUrl}}/notification/webhook/{{var::webhook.id}}
      method: delete
    handle:
      responseType: json
      actions:
        - action: tag
          path: {{var::apiName}}/data/webhooks/{{var::webhook.name}}
          type: jsonExpand
```

## webhook

Defines a webhook that enables callbacks in Ignition. The webhook takes care of checking if the callback exists with your API, creates the callback if it doesn't exist, handling the callback data, and removing the callback on shutdown.

### Parameters

**check** [function](#function) *(optional)*
The function that defines how to check if the callback exists on your API. The check function is called when the API starts up.
___
**add** [function](#function) *(optional)*
The function that adds a new callback to your API. The callback gets added if it doesn't exist. The function defines how to add the callback and automatically specifies the callback URL from Ignition.
___

**remove** [function](#function) *(optional)*
The function that removes the callback from your API. The remove function is called on restart or shutdown of the API.
___
**handle** [function](#function) *(optional)*
The function that handles the actual callback. The API will post data to Ignition using the supplied callback URL and the function will handle the response data.
> **Note:** You only need to define the actions to perform with the response data on the function. Since the API is sending the data to Ignition there is no need to define how to connect to the API within the function.

## functions

The functions section allows you to define the functions or endpoints you want to call on your API. Functions are the heart of the API Client Module that allow you to exchange data with your API.

The functions section is optional. Leaving the section out results in no functions defined. However, an API without any functions is not very useful.

### Parameters

***functionName*** [function](#function) *(required)*
You can provide multiple functions. Each function is unique by the name you provide. Replace *functionName* with the name of your function. See example below that defines 1 function called *personInfo*.

### Example

```yaml
functions:
  personInfo:
    url: {{var::baseUrl}}/person/info
    method: get
    responseType: json
    actions:
      - action: variable
        name: id
        jsonPath: $.id
```

## function

Defines a function that calls an endpoint on your API. Functions can either get information from your API and handle the response or send data to the API, such as changing a setpoint. Functions can be invoked on a schedule, to continuously get data from your API, from another function, or through scripting.

### Parameters

**method** string *(optional)*
The HTTP method to use. Possible values are:

 - get
 - post
 - put
 - delete
 - head
 - patch

Default value is *get*.
___
**depends** string *(optional)*
The name of the function that this function depends on. It forces the depends function to be called first, only once. Subsequent calls of this function will not call the depends function since it was already called.
___
**headers** list of [header](#header) *(optional)*
An array of headers to use with the request along with the global headers. Headers defined here will override global headers. If you don't define local headers, global headers will still be used.
___
**params** list of [param](#param) *(optional)*
An array of parameters to include in the URL. Typically used with a *get* method.
___
**body** [body](#body) *(optional)*
The body of the request.

**responseType** string *(optional)*
The type of response data, if required. Possible values are:

 - none - No response type, simply treat as a string (text)
 - json - JSON data
 - xml - XML data

Default value is *none*.
___
**schedule** [schedule](#schedule) *(optional)*
Provides the ability to schedule to run any frequency.
___
**actions** list of [action](#action) *(optional)*
Defines the actions that handle the response data. Only use when you want to handle the response data. You can define any number of actions that get called in the order they are defined.

## body

Defines a the body of the HTTP request. The body can be text, JSON, or form encoded parameters. Typically used with *post*, *put*, *delete*, and *patch* requests.

### Parameters

**type** string *(optional)*
The type of the body. Possible values are:

 - none - No body contents. Typically you would just leave this section out.
 - text - Allows you to define the contents of the body as a string using the *value* parameter.
 - json - Provides a JSON body either through the *value* parameter as a string or *params* parameter.
 - form - Provides www-form-encoded parameters through the *params* parameter.

Default value is *none*.
___
**value** string *(optional)* *(variable substitution)*
The value of the body as any string. You will need to specify the content type for different types of strings (XML, JSON, etc.) Use the | to define multi-line text, such as:

```yaml
value: |
  {"device":
    {"id":"{{var::person.deviceId}}"}, 
    "url":"{{var::webhook.url}}",
    "eventTypes":[{"id":"10"}]
  }
```
___
**contentType** string *(optional)*
The content type of the body, represented as a header value. Only used with *text* type.
___
**params** list of [param](#param) *(optional)*
An array of parameters to include in the JSON body or www-form-encoded parameters, based on the body type.

## schedule

Defines a the schedule to run the function at. No schedule is used if the parameter is left out. If you include the schedule parameter but don't include duration or unit, the schedule defaults to 5 minutes. See duration and unit below.

### Parameters

**duration** number *(optional)*
The duration of the time unit represented as a number. Default value is *5*.
___
**unit** string *(optional)*
The time unit of the duration. Possible values are:

 - milliseconds
 - seconds
 - minutes
 - hours
 - days

Default value is *minute*. 

## action

Defines an action that handles the response data from the API. Actions allow us to store data to Ignition, invoke other functions, or handle the response with a Python script. Actions are extremely flexible and powerful.

### Parameters

**action** string *(required)*
The type of action to use. Possible values are:

 - [variable](#actionvariable)
 - [tag](#tag)
 - [script](#script)
 - [function](#actionfunction)

Each action has its own set of parameters. See the actions below for more details.

## variable (action)<span id="actionvariable"><span> 

Defines a variable action that stores response data to variables on the function. These variables are different from the API variables and are only available once the function is called. These variables are not persistent. However, they can be used in variable substitution in other functions. Very useful when you call one endpoint, store a value from the response as variable, and use it in another endpoint. Helpful when a function depends on another function. See this example:

```yaml
functions:
  personInfo:
    url: {{var::baseUrl}}/person/info
    method: get
    responseType: json
    actions:
      - action: variable
        name: id
        jsonPath: $.id
  person:
    depends: personInfo
    url: {{var::baseUrl}}/person/{{var::personInfo.id}}
    method: get
    responseType: json
    actions:
      - action: tag
        path: {{var::apiName}}/data/person
        type: jsonExpand
```

When you call the *person* function, the *personInfo* function gets called first since it depends on it. The *personInfo* function stores the id from the result as a variable called *id*. The *id* is used in the *person* function in the URL, by specifying where the variable comes from (variable store called *personInfo*): `{{var::baseUrl}}/person/{{var::personInfo.id}}`

A variable can either get its value by specifying the value as a string, reading the value of an Ignition tag, or getting a value from the response data.

### Parameters

**name** string *(required)*
The name of the variable. Used with variable substitution to identify the variable.
___
**value** string *(optional)* *(variable substitution)*
Allows you to specify the value of the variable as a string.
___
**jsonPath** string *(optional)*
Allows you to specify the value of the variable by getting a specific value out of the JSON response by specifying a path. Only works with JSON responses. See [JSON Path](#json-path) for more details.
___
**tagPath** string *(optional)* *(variable substitution)*
Allows you to specify the value of the variable by reading the value of an Ignition tag. Make sure to specify fully qualified tag paths, such as:

    [default]path/to/my/tag

## tag

Defines a tag action that stores response data to tags in Ignition. The API Client Module creates a tag provider, called ***API***, that the tags show up in. Tags will automatically get created for you. The action provides the ability to write individual values or entire responses. The tag action also provides the ability to create UDT definition and instances, making it easier to define configuration on those tags in Ignition. Here is an example that takes the entire JSON response and expands that into Ignition tags:

```yaml
actions:
  - action: tag
    path: {{var::apiName}}/clients
    type: jsonExpand
```

### Parameters

**type** string *(required)*
The type of tag action to use. Possible values are:

 - jsonWrite - Writes individual values of the JSON response. Requires JSON response data.
 - jsonExpand - Writes the entire JSON response to tags. Requires JSON response data.
 - text - Writes the entire response, as a string, to a tag.
___
**path** string *(optional)* *(variable substitution)*
The path, or folder, to create the tags. Leave empty for the root of the tag provider. Each tag can define a path that is in addition to the path defined here. Default value is the name of the API when not defined.
___
**tags** list of [tag](#actiontag) *(optional)*
Allows you to define one or more specific tags you want to create.
___
**udts** list of [udt](#udt) *(optional)*
Allows you to define UDTs from the response.

## script

Defines a script action that calls a Python script function defined in Ignition. This allows you to handle the response on your own through scripting. The script action assumes your function is defined with the following arguments:

 - statusCode - The integer status code of the response
 - contentType - The content type of the response
 - response - The actual response as a string

Here is an example function:

```python
def handleResponse(statusCode, contentType, response):
    # your code goes here
```

You just need to specify the function path like so:

```yaml
script:
  project: MyProject
  script: api.handleResponse
```

### Parameters

**project** string *(optional)*
The name of the project that holds the function. If you leave out the project, we assume the function is defined on the Gateway.
___
**script** string *(required)*
The full path to the script function to use.

## function (action)<span id="actionfunction"><span>

Defines a function action that calls another function you have defined. Perfect for calling another function automatically from the response.

### Parameters

**function** string *(required)*
The name of the function to invoke.
___
**jsonPath** string *(optional)*
Allows you to invoke the function multiple times with all of the paths found in the JSON response. Perfect for calling the function on multiple devices that are returned. Variables defined with a JSON path will be appended to this JSON path, allowing you to get specific data out of each section. Leave out if you only want to invoke the function once. See [JSON Path](#json-path) for more details.
___
**variables** list of [variable](#actionvariable) *(required)*
The list of variables you want to store on the handler. You can use these variables in the function with the store called *handler*: `{{var::handler.id}}`

## tag (action)<span id="actiontag"><span>

Defines a tag you want to create in the API tag provider. A tag can either be defined with a static value or from response data. By default, we create memory tags. However, it is possible to create expression tags and derived tags. Here is an example of a tag with a static value:

```yaml
tags: 
  - path: start
    name: runDuration
    dataType: Int4
    defaultValue: 0
```

### Parameters

**name** string *(required)*
The name of the tag.
___
**dataType** string *(required)*
The data type of the tag. Possible values are:

 - Int1 - 1 byte
 - Int2 - 2 bytes
 - Int4 - 4 bytes
 - Int8 - 8 bytes
 - Float4 - 4 bytes
 - Float8 - 8 bytes
 - Boolean
 - String
 - DateTime
 - Text
 - Int1Array
 - Int2Array
 - Int4Array
 - Int8Array
 - Float4Array
 - Float8Array
 - BooleanArray
 - StringArray
 - DateTimeArray
 - ByteArray
 - DataSet
 - Document

___
**path** string *(optional)* *(variable substitution)*
The path of the tag.
___
**defaultValue** string *(optional)*
The default value of the tag.
___
**jsonPath** string *(optional)*
Allows you to specify the value of the tag by getting a specific value out of the JSON response by specifying a path. Only works with JSON responses. See [JSON Path](#json-path) for more details.
___
**expression** string *(optional)*
Allows you to define an expression tag in Ignition. This will be the expression to define on the tag. Use relative tag paths to stay within your folder. See Ignition's documentation on [expression tags](https://docs.inductiveautomation.com/display/DOC81/Types+of+Tags#TypesofTags-ExpressionTags).
___
**derived** [derived](#derived) *(optional)*
Allows you to define a derived tag in Ignition.
___
**handler** [handler](#handler) *(optional)*
Allows you to define a tag write handler, i.e. allows you to write values to the created tag that you can handle by invoking other functions.

## udt

Defines a UDT definition to create in the API tag provider. UDTs (User Defined Types), also referred to as Complex Tags, offer the ability to leverage object-oriented data design principles in Ignition. UDTs are extremely important in Ignition. With UDTs, you can dramatically reduce the amount of work necessary to create robust systems by essentially creating parameterized "data templates". The UDT definition will compose of all of the tags within the JSON sub elements represented by the JSON path. If there are multiple UDT instances, the UDT definition will contain the aggregate of all of the tags across all instances. UDTs only work with JSON paths, which locate all of the UDT instances.

Here is an example of UDT definition:

```yaml
udts:
  - id: zone
    path: {{var::apiName}}/zone
    jsonPath: $.devices[*].zones[*]
    name: $.name
```

More information on UDTs found in the [documentation](https://docs.inductiveautomation.com/display/DOC81/User+Defined+Types+-+UDTs).

### Parameters

**id** string *(required)*
The identifier of the UDT.
___
**path** string *(optional)* *(variable substitution)*
The path of the UDT definition. Defaults to `APIName/UDTId` when left out.
___
**name** string *(optional)*
The name override of the UDT instance as a JSON path. Allows you to use a value within the UDT as the instance name. The JSON path should be relative to the UDT parent. Only works for JSON responses. For example, if you want to use the name value inside of the UDT, the JSON path would be:

    $.name

See [JSON Path](#json-path) for more details.
___
**jsonPath** string *(optional)*
Allows you to locate all of the UDT instances in the JSON response using a JSON path. The path can reference one or more values. For example, if you want to point to all of the devices in an array, use the following JSON path:

    $.devices[*]

Only works with JSON responses. See [JSON Path](#json-path) for more details.
___
**tags** list of [tag](#actiontag) *(optional)*
Allows you to define one or more specific tags you want to create in the UDT. Any conflicts with the tags found from the result with these tags will result in a merge of the configuration.

## derived

Defines a derived tag you want to create in the API tag provider. See Ignition's documentation on [derived tags](https://docs.inductiveautomation.com/display/DOC81/Types+of+Tags#TypesofTags-DerivedTags).

### Parameters

**source** string *(required)*
The source tag path to use in the derived tag. Provide an Ignition tag path without curly braces {}.
___
**read** string *(required)*
The read expression to use in the derived tag for translating the source value. Provide a standard Ignition expression. Use `{source}` to reference the value of the source tag.
___
**write** string *(required)*
The write expression to use in the derived tag for translating the written value. Provide a standard Ignition expression. Use `{value}` to reference the written value.

## handler

Defines a tag write handler. This allows you to either modify the tag value, making it a true memory tag, or invoke a function when the value changes. Useful for changing a setpoint or turning a light on or off through tags. It avoids having to invoke a function through scripting.

### Parameters

**function** string *(optional)*
The function to invoke. Leave out if you just want to allow writing to the tag with no function handler.
___
**reset** boolean *(optional)*
Automatically resets the tag's value back to false. Used for boolean tags where you want to invoke the handler on the rising edge only.
___
**variables** list of [variable](#actionvariable) *(required)*
The list of variables you want to store on the handler. You can use these variables in the function with the store called *handler*: `{{var::handler.id}}`

# JSON Path

JsonPath expressions always refer to a JSON structure in the same way as XPath expression are used in combination 
with an XML document. The "root member object" in JsonPath is always referred to as `$` regardless if it is an 
object or array.

JsonPath expressions can use the dot–notation

`$.store.book[0].title`

or the bracket–notation

`$['store']['book'][0]['title']`

## Operators

| Operator                  | Description                                                        |
| :------------------------ | :----------------------------------------------------------------- |
| `$`                       | The root element to query. This starts all path expressions.       |
| `@`                       | The current node being processed by a filter predicate.            |
| `*`                       | Wildcard. Available anywhere a name or numeric are required.       |
| `..`                      | Deep scan. Available anywhere a name is required.                  |
| `.<name>`                 | Dot-notated child                                                  |
| `['<name>' (, '<name>')]` | Bracket-notated child or children                                  |
| `[<number> (, <number>)]` | Array index or indexes                                             |
| `[start:end]`             | Array slice operator                                               |
| `[?(<expression>)]`       | Filter expression. Expression must evaluate to a boolean value.    |


## Functions

Functions can be invoked at the tail end of a path - the input to a function is the output of the path expression.
The function output is dictated by the function itself.

| Function                  | Description                                                         | Output    |
| :------------------------ | :------------------------------------------------------------------ |-----------|
| min()                     | Provides the min value of an array of numbers                       | Double    |
| max()                     | Provides the max value of an array of numbers                       | Double    |
| avg()                     | Provides the average value of an array of numbers                   | Double    |
| stddev()                  | Provides the standard deviation value of an array of numbers        | Double    |
| length()                  | Provides the length of an array                                     | Integer   |
| sum()                     | Provides the sum value of an array of numbers                       | Double    |
| keys()                    | Provides the property keys (An alternative for terminal tilde `~`)  | `Set<E>`  |

## Filter Operators

Filters are logical expressions used to filter arrays. A typical filter would be `[?(@.age > 18)]` where `@` represents the current item being processed. More complex filters can be created with logical operators `&&` and `||`. String literals must be enclosed by single or double quotes (`[?(@.color == 'blue')]` or `[?(@.color == "blue")]`).   

| Operator                 | Description                                                           |
| :----------------------- | :-------------------------------------------------------------------- |
| ==                       | left is equal to right (note that 1 is not equal to '1')              |
| !=                       | left is not equal to right                                            |
| <                        | left is less than right                                               |
| <=                       | left is less or equal to right                                        |
| >                        | left is greater than right                                            |
| >=                       | left is greater than or equal to right                                |
| =~                       | left matches regular expression  [?(@.name =~ /foo.*?/i)]             |
| in                       | left exists in right [?(@.size in ['S', 'M'])]                        |
| nin                      | left does not exists in right                                         |
| subsetof                 | left is a subset of right [?(@.sizes subsetof ['S', 'M', 'L'])]       |
| anyof                    | left has an intersection with right [?(@.sizes anyof ['M', 'L'])]     |
| noneof                   | left has no intersection with right [?(@.sizes noneof ['M', 'L'])]    |
| size                     | size of left (array or string) should match right                     |
| empty                    | left (array or string) should be empty                                |


## Path Examples

Given the json

```javascript
{
    "store": {
        "book": [
            {
                "category": "reference",
                "author": "Nigel Rees",
                "title": "Sayings of the Century",
                "price": 8.95
            },
            {
                "category": "fiction",
                "author": "Evelyn Waugh",
                "title": "Sword of Honour",
                "price": 12.99
            },
            {
                "category": "fiction",
                "author": "Herman Melville",
                "title": "Moby Dick",
                "isbn": "0-553-21311-3",
                "price": 8.99
            },
            {
                "category": "fiction",
                "author": "J. R. R. Tolkien",
                "title": "The Lord of the Rings",
                "isbn": "0-395-19395-8",
                "price": 22.99
            }
        ],
        "bicycle": {
            "color": "red",
            "price": 19.95
        }
    },
    "expensive": 10
}
```

| JsonPath (click link to try)| Result |
| :------- | :----- |
| <a href="http://jsonpath.herokuapp.com/?path=$.store.book[*].author" target="_blank">$.store.book[*].author</a>| The authors of all books     |
| <a href="http://jsonpath.herokuapp.com/?path=$..author" target="_blank">$..author</a>                   | All authors                         |
| <a href="http://jsonpath.herokuapp.com/?path=$.store.*" target="_blank">$.store.*</a>                  | All things, both books and bicycles  |
| <a href="http://jsonpath.herokuapp.com/?path=$.store..price" target="_blank">$.store..price</a>             | The price of everything         |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[2]" target="_blank">$..book[2]</a>                 | The third book                      |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[2]" target="_blank">$..book[-2]</a>                 | The second to last book            |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[0,1]" target="_blank">$..book[0,1]</a>               | The first two books               |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[:2]" target="_blank">$..book[:2]</a>                | All books from index 0 (inclusive) until index 2 (exclusive) |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[1:2]" target="_blank">$..book[1:2]</a>                | All books from index 1 (inclusive) until index 2 (exclusive) |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[-2:]" target="_blank">$..book[-2:]</a>                | Last two books                   |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[2:]" target="_blank">$..book[2:]</a>                | Book number two from tail          |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[?(@.isbn)]" target="_blank">$..book[?(@.isbn)]</a>          | All books with an ISBN number         |
| <a href="http://jsonpath.herokuapp.com/?path=$.store.book[?(@.price < 10)]" target="_blank">$.store.book[?(@.price < 10)]</a> | All books in store cheaper than 10  |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[?(@.price <= $['expensive'])]" target="_blank">$..book[?(@.price <= $['expensive'])]</a> | All books in store that are not "expensive"  |
| <a href="http://jsonpath.herokuapp.com/?path=$..book[?(@.author =~ /.*REES/i)]" target="_blank">$..book[?(@.author =~ /.*REES/i)]</a> | All books matching regex (ignore case)  |
| <a href="http://jsonpath.herokuapp.com/?path=$..*" target="_blank">$..*</a>                        | Give me every thing   
| <a href="http://jsonpath.herokuapp.com/?path=$..book.length()" target="_blank">$..book.length()</a>                 | The number of books                      |
