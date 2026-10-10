<img height="500" alt="logo" src="https://github.com/user-attachments/assets/5c3d03cb-dc49-49ef-97e9-115cd6321274" />


## Sprout

Sprout is an opinionated boilerplate generator tool to work with the VirtusLab Scala Stack. 
Draws inspiration from Laravel's artisan command. 

It's ideal for greenfield projects that have yet to write models and basic routing to a server.
It's currently at an experimental stage. 

## Stack

- Scala 3.9
- Tapir endpoint definitions
- Sttp: http client
- Jsoniter: for JSON serialization
- SnakeYaml: for loading and parsing specs

### Usage

Sprout currently has two commands: new and forage.

### New
A command that allows you to create a model or router.


For Sprout to work, you must have a schemas folder under src/main/resources/ containing your .yaml
Schemas Definition. By default, files are written into a sprout package so it doesn't mess with your pre-existing packages.

#### Schemas
This example has a modelDef (the definition of your case class) and endpoints (the definition
of your endpoints). You may omit any of them as the [SchemaParser](src/main/scala/spec/SchemaParser.scala)
knows how to read and can choose according to the flags passed through the command prompt.

```yaml
name: User
modelDef:
  - name: id
    valueType: Long
  - name: name
    valueType: String
endpoints:
  - path: users
    method: post
    request: User
    response: User
```

```bash
sprout new:model name [--with-router, -wr] # adds a simple router
```

This will produce a file under sprout/models and a router if --with-router/-wr was passed.

```bash
sprout new:router name [[--api, -a [--model, -m]] # --api/-a adds api restful endpoints
# this must then receive a -m --model flag to choose an existing model based on the Router name
# i.g. new:router User --api --model -> implies a search for an User model under sprout/models
```
This will produce a file under sprout/routers.

### Forage

This command is to be used after you have generated files under /sprout. 
Forage walks through your schemas folder and produces the definitions declared
and at last, bootstraps all routes into a Server.scala wiring routes and endpoints.

 ```bash
sprout forage 
```
### Limitations / Warnings

- **Sprout by default overwrites. I'd advise you keep sprout files and your actual models with implementations and endpoint logic separate. Not only to avoid
  overwrites but as a way to separate the actual wiring from logic.**
- Code is produced from Strings, so type safety isn't guaranteed. Will look in the future a way to handle this if it gets big enough. For now, we are trading soundness for simplicity.
- Ox isn't included by default yet. Will probably do it next.
- It's a WIP so a lot of things are bound to change.
