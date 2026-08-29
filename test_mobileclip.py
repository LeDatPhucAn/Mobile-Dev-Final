import torch
import open_clip

from mobileclip.modules.common.mobileone import reparameterize_model


MODEL_PATH = r"D:\MobileImageRetrieval\MobileCLIP2-S0\mobileclip2_s0.pt"

print("OpenCLIP version:", open_clip.__version__)
print("MobileCLIP2-S0 available:", "MobileCLIP2-S0" in open_clip.list_models())

model, _, preprocess = open_clip.create_model_and_transforms(
    "MobileCLIP2-S0",
    pretrained=MODEL_PATH,
    image_mean=(0, 0, 0),
    image_std=(1, 1, 1),
)

tokenizer = open_clip.get_tokenizer("MobileCLIP2-S0")

print("Loaded checkpoint.")

model.eval()

print("Eval mode enabled.")

model = reparameterize_model(model)

print("Reparameterized.")

tokens = tokenizer([
    "a cat",
    "a dog",
    "a photo of food",
])

print("Token tensor:", tokens.shape)

with torch.no_grad():
    text_embedding = model.encode_text(tokens)
    text_embedding = text_embedding / text_embedding.norm(
        dim=-1,
        keepdim=True
    )

print("Text embeddings:", text_embedding.shape)
print("Everything works.")