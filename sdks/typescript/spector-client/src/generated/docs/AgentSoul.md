
# AgentSoul


## Properties

Name | Type
------------ | -------------
`id` | string
`name` | string
`description` | string
`systemPrompt` | string
`purpose` | string
`personality` | string
`expertiseDomains` | Array&lt;string&gt;
`coreValues` | Array&lt;string&gt;
`ethicalGuardrails` | Array&lt;string&gt;
`emotionalBaseline` | [EmotionalBaseline](EmotionalBaseline.md)
`communicationStyle` | string
`model` | string
`tools` | Array&lt;string&gt;
`expertiseEmbedding` | Array&lt;number&gt;
`purposeEmbedding` | Array&lt;number&gt;
`soulVersion` | number
`createdAt` | Date
`updatedAt` | Date
`present` | boolean

## Example

```typescript
import type { AgentSoul } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "name": null,
  "description": null,
  "systemPrompt": null,
  "purpose": null,
  "personality": null,
  "expertiseDomains": null,
  "coreValues": null,
  "ethicalGuardrails": null,
  "emotionalBaseline": null,
  "communicationStyle": null,
  "model": null,
  "tools": null,
  "expertiseEmbedding": null,
  "purposeEmbedding": null,
  "soulVersion": null,
  "createdAt": null,
  "updatedAt": null,
  "present": null,
} satisfies AgentSoul

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AgentSoul
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


