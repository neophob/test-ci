# legal-i Agent

## Component and Delivery

- The legal-i agent is a dockerized Spring Boot application that ensures solid two-way communication between the customers' environment and the legal-i cloud.
- The component has two sides: The SDK that talks to the cloud and the connector that talks to the customer system.
- The latest version is available on legal-i's private GitHub repository.

## Quickstart 1234

*Prerequisites*

- Access to legal-i's GitHub repository on https://github.com/legal-i/agent-example
- Agent credentials to access your environment on the legal-i cloud
- Recent docker and docker-compose versions installed
- Internet-Access to `*.legal-i.ch`

1. Set your legal-i cloud credentials in `quickstart/agent.env`
2. In the `quickstart`-directory, run `docker-compose build`.
3. Run `docker-compose up agent`
4. The agent runs and starts transmitting data
5. Check the agent status in the UI (menu in avatar).
6. In case you need monitoring or proxy support, you can start the corresponding services
	1. Monitoring: `docker-compose up prometheus grafana`
		1. Open Grafana at http://localhost:3000/ (admin/admin)
		2. Add Prometheus datasource pointing to http://prometheus:9090/
		3. Currently, there is no default dashboard defined.
	2. Http Proxy: `docker-compose up squid`
		1. Adapt `agent.env` to use the proxy

### Monitoring

```
# Liveness (agent is up)
http://localhost:8085/actuator/health/liveness

# Readiness (agent can connect to the legal-i cloud)
http://localhost:8085/actuator/health/readiness

# Prometheus metrics
http://localhost:8085/actuator/prometheus

```

## Development

Make sure to set the secrets correctly via environment variables or a properties file:

```
LEGALI_API_URL=<>
LEGALI_CLIENT_ID=<>
LEGALI_CLIENT_SECRET=<>
```

In the Example Connector, you can see how the different APIs are called. JavaDoc can be downloaded for the SDK module
for further explanation and thrown exceptions.

### Overview:

- After the Spring Boot application is initialized, the agent tries to connect to the legal-i endpoints.
- If the connection can be established, an `HealthService.StartConnectorEvent` Event is published.
- This has the following effects:
	- Upon receiving this event, the `ExampleService` runs `ExampleThreads`. Those Example threads call some of the
	available APIs.
		- The number of threads and the runs per thread can be configured
		- Further, a path can be specified for choosing PDF files
	- The `ExampleRemoteEventService` starts listening to events that are triggered on the API.
		- As an example, he requests a `pong`-Event from the API.
		- This pong will be sent by the API asynchronously and be visible in the EventHandler
- SDK entities and methods contain JavaDoc annotations.

### File Transfer
To keep a constant memory footprint on the Agent, the SDK uses a FileObject instead of a ByteArrayResource. PDF files can be large if they contain images (> 500MB). In multi-threaded mode, this leads to unwanted spikes in
memory usage.

The SDK supports two file transfer types:
- CLOUDFRONT: The file is uploaded via AWS CloudFront to the ingest S3 bucket. This is generally faster and more stable but might require additional outgoing network permissions.
- LEGALI: The file is proxied through the legal-i file service. Do not use this in production unless there are network restrictions.
```
legali.fileservice = CLOUDFRONT
```

### Entity Metadata

`LegalCase` and `SourceFiles` Entities contain a metadata field to add integration-specific key-value store, with
type `string` / `string`. Defaults can be set in the application config or via environment variables. Currently, the
following keys are supported:

```
SourceFile
# set dossier, supported value, see below. defaults to unknown
legali.dossiertype       = accident

# set doc type, supported values see below
legali.doctype          = type_medical_report

# set the issue date
legali.issuedate        = 2020-01-01

# set the document title
legali.title            = Dokumenttitel

# if multiple languages are available, suffix with two letter language key
legali.title_fr         = titre du document

# document language in two-character key. If empty, it's detected
legali.lang             = de

Technical
# Disables processing for this file (to test APIs and integration)
legali.pipeline.disabled = true

```

### Events

The agent must subscribe to the events it wants to receive.

```
@PostConstruct
public void init() {
	this.eventService.subscribe(PongEvent.class, ExportPublishedEvent.class);
}
```

After subscribing, the Agent can listen for events by creating event listener methods.
Annotate methods with the `@EventListener` annotation and specify the event class as the method parameter.

```
@EventListener
public void handle(PongEvent event) {
	log.info("🏓 PingPong Event received: " + "\nid " + event.getUuid());
	// Ack the event
	this.applicationEventPublisher.publishEvent(new ConfirmEventRequest(event.getUuid()));
}
```

The following events are currently supported:

`PongEvent`
Emitted after a PongRequest is requested for debugging.
Request with `this.eventClient.ping(new PingRequest("ping"));`

`LegalCaseCreatedEvent`
Emitted when a user creates a new legal case via the frontend.

`LegalCaseUpdatedEvent`
Emitted when a user updates a legal case via the frontend.

`LegalCaseStatusChangedEvent`
Emitted when a user changes the status of a legal case via the frontend, (OPEN, ARCHIVED).

`LegalCaseReadyEvent`
Emitted when all sourcefiles of a legal case are successfully processed.

`SourceFileCreatedEvent`.
Emitted when a user creates a source file via the frontend.

`SourceFileUpdatedEvent`
Emitted when a user changes the Dossier Type (aka Field / Akte) in the frontend

`SourceFileTaskFailedEvent`
Emitted by the pipeline when processing of the source file failed.

`ExportCreatedEvent`
Emitted when a user creates a new export through the frontend.

`ExportPublishedEvent`
Emitted when a user publishes a new export with a link or an email.

`ExportViewedEvent`
Emitted when an external user opens/downloads an exported pdf.

The event handler must send back a confirmation to the API. When the API does not receive a confirmation, it will send the event
again after 5 minutes.
```
this.applicationEventPublisher.publishEvent(new ConfirmEventRequest(event.getUuid()));
```
Every event contains a unique id that needs to be sent back to confirm the event (ACK).


## Build, create docker image and run

See Makefile as a reference:

```
make ...
lint        run verify and skip tests
verify      run verify with tests
build       build the agent
dockerize   create agent docker image tagged legali-agent
run         run docker image
```

## Configuration and Deployment

All configurations can be set as environment variables by Spring Boot configuration convention.

````
# Example Connector Config, set iterations and threads
legali.example.iterations=1
spring.task.execution.pool.max-size=1
spring.task.execution.pool.core-size=1

# Disable processing pipeline for development (do not use in production)
legali.default-metadata.legali.pipeline.disabled=true

# Endpoint and credentials for legal-i cloud
legali.api-url=<>
legali.client-id=<>
legali.client-secret=<>

# Upload via cloudfront or proxied via API, use cloud front upload whenever possible
legali.cloud-front-upload=true

# HTTP connection
#legali.request-connection-timeout-seconds=30
#legali.max-connection-retries=5
#legali.request-read-timeout-seconds=90
#legali.max-failed-heartbeats=5

# Proxy setup
#legali.http-proxy-host=localhost
#legali.http-proxy-port=3128

# Logging and Debugging
logging.level.root=INFO
logging.level.ch.legali.sdk.example=INFO
logging.level.ch.legali.sdk.sdk=INFO
logging.level.ch.legali.sdk.sdk.internal=WARN

# Debug HTTP connection
#logging.level.feign = DEBUG
#legali.feign-log-level=FULL

# Monitoring
server.port=8085
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
management.endpoint.health.group.readiness.include=readinessState,agent
````

### Internet Access

The agent needs to be allowed to access `*.legal-i.ch` on 443.

## References


### Dossier Types

```
unknown           : Anderes (default)
health            : Krankenversicherung
health_allowance  : Krankentaggeld (KVG und VVG)
liability         : Haftpflichtrecht
bvg               : BVG
vvg               : VVG
accident          : Unfall
suva              : Unfall (SUVA)
iv-ag             : IV Aargau
iv-ai             : IV Appenzell Innerrhoden
iv-ar             : IV Appenzell Ausserrhoden
iv-be             : IV Bern
iv-bl             : IV Basel-Landschaft
iv-bs             : IV Basel-Stadt
iv-fr             : IV Freiburg
iv-ge             : IV Genf
iv-gl             : IV Glarus
iv-gr             : IV Graubünden
iv-ju             : IV Jura
iv-lu             : IV Luzern
iv-ne             : IV Neuenburg
iv-nw             : IV Nidwalden
iv-ow             : IV Obwalden
iv-sg             : IV St. Gallen
iv-sh             : IV Schaffhausen
iv-so             : IV Solothurn
iv-sz             : IV Schwyz
iv-tg             : IV Thurgau
iv-ti             : IV Tessin
iv-ur             : IV Uri
iv-vd             : IV Waadt
iv-vs             : IV Wallis
iv-zg             : IV Zug
iv-zh             : IV Zürich
army              : Militärversicherung
```

### Document Types

```
type_admin    Admin
type_admin_claim_report_uvg         : Schaden- / Krankheitsmeldungen
type_admin_facts_sheet              : Feststellungsblätter
type_admin_iv_registration          : IV Anmeldungen
type_admin_protocol                 : Protokolle
type_admin_table_of_content         : Inhaltsverzeichnisse
type_correspondence                 : Korrespondenz
type_correspondence_external_emails : Externe Emails
type_correspondence_internal_emails : Interne Emails
type_correspondence_letters         : Briefe
type_financial                      : Finanziell
type_financial_allowance_overview   : Taggeld-Abrechnungen
type_financial_invoice              : Rechnungen
type_internal                       : Interne-Dokumente
type_internal_antifraud             : Akten der internen Betrugsbekämpfungsstelle
type_internal_reports               : Interne Berichte
type_legal                          : Rechtlich
type_legal_attorney_submission      : Anwaltliche Eingaben
type_legal_court_decision           : Urteile
type_legal_criminal_file            : Strafakten
type_legal_disposal                 : Verfügungen
type_legal_objection                : Einsprachen
type_legal_objection_decision       : Einsprache-Entscheide
type_legal_pre_disposal             : Vorbescheide / Formlose Ablehnungen
type_legal_proxy                    : Vollmachten
type_legal_submissions_court        : Eingaben Gerichtsverfahren
type_medical                        : Medizinisch
type_medical_certificate            : AUF-Zeugnisse
type_medical_cost_credit            : Kostengutsprachen
type_medical_expert_opinion         : Gutachten
type_medical_insurance_report       : Vers. interne Arztberichte
type_medical_prescription           : Med. Verordnungen
type_medical_report                 : Arztberichte
type_other                          : Andere
type_other_phone_memo               : Telefon- / Aktennotizen
type_profession                     : Berufliches
type_profession_cv                  : Lebensläufe
type_profession_employment_contract : Arbeitsverträge
type_profession_ik_statement        : IK-Auszüge
type_profession_questionnaire       : Arbeitgeberfragebogen
type_profession_wage_statements     : Lohnabrechnungen
```

## IAM Integration
Users are included with single sign-on. Roles and permissions are managed by group memberships.


### Enterprise IDP connections
See https://auth0.com/docs/connections/enterprise

### Roles and Authorization
Every user needs to have at least one valid legal-i role to access legal-i. The role is given to the user by assigning him a group with a specific pattern.

- **Tenant Admin**
- has group that contains `*legali_admin*`
- has access to...
	- all legal cases (without permission check)
	- admin functions and agent panel
	- can crud legalcases and sourcefiles


- **Power User**
	- has group that contains `*legali_power*`
	- has access to all legal cases
	- can crud legalcases and sourcefile


- **Basic**
	- has group that contains `*legali_basic*`
	- has access to...
		- cases that he has access (see permission groups)

### Permission groups
All other groups that contain `*legali*` are used as permission groups.
A basic user only has access to a legal case if they have at least one matching group.
